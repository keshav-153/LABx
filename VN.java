
import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.util.*;

public class VN {
    /////////////////////////////////////////////////////////
    /// ///////// neighbour class stores data about a neighbouring VN
    static class Neighbour {
        int IPaddress, UDPport, cost;
        char name;

        Neighbour(int ip, int udp, int c, char n) {
            IPaddress = ip;
            UDPport = udp;
            cost = c;
            name = n;
        }
    }

    static final Object lock = new Object();

    //////////// topology is a local copy of the map
    static final Map<Character, List<Neighbour>> topology = new HashMap<>();
    
    /////////// maps the origin vn to last seq received from it
    static final Map<Character, Integer> lastSeq = new HashMap<>();

    static DatagramSocket udpSocket;
    static char myId; //// a/b/c/d/e
    static int sequence = 0;

    static int ipToInt(InetAddress ip) {
        return ByteBuffer.wrap(ip.getAddress()).getInt();
    }

    static InetAddress intToIP(int ip) throws UnknownHostException {
        return InetAddress.getByAddress(
            ByteBuffer.allocate(4).putInt(ip).array()
        );
    }

    /////////////////////////////////// 
    /// ///////// readLinks reads a list of links from the ON's Link state packet
    static List<Neighbour> readLinks(DataInputStream in)
            throws IOException {
        int count = in.readUnsignedShort();
        List<Neighbour> links = new ArrayList<>();

        for (int i = 0; i < count; i++) {
            char name = (char) in.readUnsignedByte(); // 1Byte
            int ip = in.readInt();                    // 4Bytes
            int udp = in.readUnsignedShort();         // 2Bytes
            int cost = in.readUnsignedByte();       // 1Byte

            links.add(new Neighbour(ip, udp, cost, name)); //total 8byte
        }

        return links;
            }

    //////////////////////////////////////
    /// creating a linnk state packet to send to my neighbours
    static byte[] makeLSP(char origin, int seq,
                          List<Neighbour> links) {
        ByteBuffer buffer = ByteBuffer.allocate(7 + 8 * links.size());

        buffer.put((byte) origin);
        buffer.putInt(seq);
        buffer.putShort((short) links.size());

        for (Neighbour n : links) {
            buffer.put((byte) n.name); //1
            buffer.putInt(n.IPaddress); //4
            buffer.putShort((short) n.UDPport); //2
            buffer.put((byte) n.cost); //1
        }

        return buffer.array(); //8B
    }
    
    ////////////////////////////////////////
    /// forwarding a received lsp to other neighbours

    static void sendToNeighbours(byte[] data, InetAddress source,
                                 int sourcePort) throws IOException {
        List<Neighbour> direct;

        synchronized (lock) {
            direct = new ArrayList<>(
                topology.getOrDefault(myId, Collections.emptyList())
            );
        }

        for (Neighbour n : direct) {
            InetAddress destination = intToIP(n.IPaddress);

            if (source != null && n.UDPport == sourcePort
                    && destination.equals(source)) {
                continue;
            }

            DatagramPacket packet = new DatagramPacket(
                data, data.length, destination, n.UDPport
            );

            udpSocket.send(packet);
        }
    }

    ////////////////////////////////////
    /// creating my own lsp and sending it to my neighbours
    /// important to update sequence number and lastSeq map

    static void originateLSP() throws IOException {
        byte[] packet;
        int seq;

        synchronized (lock) {
            List<Neighbour> direct = new ArrayList<>(
                topology.getOrDefault(myId, Collections.emptyList())
            );

            seq = ++sequence;
            lastSeq.put(myId, seq);
            packet = makeLSP(myId, seq, direct);
        }

        sendToNeighbours(packet, null, -1);
    }


    static void handleONUpdate(List<Neighbour> links,
                               int localIP, int localPort)
            throws IOException {
        char id = 0;
        boolean found = false;
        List<Neighbour> direct = new ArrayList<>();

        for (Neighbour n : links) {
            if (n.IPaddress == localIP && n.UDPport == localPort) {
                id = n.name;
                found = true;
            } else {
                direct.add(n);
            }
        }

        if (!found) {
            throw new IOException("Could not identify this VN in ON's update.");
        }

        synchronized (lock) {
            if (myId != 0 && myId != id) {
                throw new IOException("ON changed this VN's ID.");
            }

            myId = id;
            topology.put(myId, direct);
        }

        originateLSP();
    }

    static void receiveLSP() {
        byte[] data = new byte[65507];

        while (!udpSocket.isClosed()) {
            DatagramPacket packet = new DatagramPacket(data, data.length);

            try {
                udpSocket.receive(packet);

                if (packet.getLength() < 7) {
                    continue;
                }

                ByteBuffer buffer = ByteBuffer.wrap(
                    packet.getData(),
                    packet.getOffset(),
                    packet.getLength()
                );

                char origin = (char) (buffer.get() & 0xff);
                int seq = buffer.getInt();
                int count = buffer.getShort() & 0xffff;

                if (buffer.remaining() != count * 8) {
                    continue;
                }

                List<Neighbour> links = new ArrayList<>();

                for (int i = 0; i < count; i++) {
                    char name = (char) (buffer.get() & 0xff);
                    int ip = buffer.getInt();
                    int udp = buffer.getShort() & 0xffff;
                    int cost = buffer.get() & 0xff;

                    links.add(new Neighbour(ip, udp, cost, name));
                }

                System.out.println("LSP received at VN " + myId
                    + " | Origin: " + origin
                    + " | Sequence: " + seq
                    + " | Links: " + links.size());

                for (Neighbour n : links) {
                    try {
                        System.out.println("  " + origin + " -> " + n.name
                            + " | IP: " + intToIP(n.IPaddress).getHostAddress()
                            + " | UDP Port: " + n.UDPport
                            + " | Cost: " + n.cost);
                    } catch (UnknownHostException e) {
                        System.out.println("  " + origin + " -> " + n.name
                            + " | IP: " + n.IPaddress
                            + " | UDP Port: " + n.UDPport
                            + " | Cost: " + n.cost);
                    }
                }
                System.out.println();

                boolean fresh;

                synchronized (lock) {
                    fresh = origin != myId
                        && (!lastSeq.containsKey(origin)
                            || seq > lastSeq.get(origin));

                    if (fresh) {
                        lastSeq.put(origin, seq);
                        topology.put(origin, links);
                    }
                }

                if (fresh) {
                    sendToNeighbours(
                        Arrays.copyOfRange(
                            packet.getData(),
                            packet.getOffset(),
                            packet.getOffset() + packet.getLength()
                        ),
                        packet.getAddress(),
                        packet.getPort()
                    );
                }

            } catch (IOException e) {
                if (!udpSocket.isClosed()) {
                    System.err.println("UDP receive error: " + e.getMessage());
                }
                return;
            }
        }
    }

    static Integer findCost(Map<Character, List<Neighbour>> db,
                            char from, char to) {
        for (Neighbour n :
                db.getOrDefault(from, Collections.emptyList())) {
            if (n.name == to) {
                return n.cost;
            }
        }

        return null;
    }

    static void printMatrix() {
        Map<Character, List<Neighbour>> db = new HashMap<>();
        TreeSet<Character> nodes = new TreeSet<>();

        synchronized (lock) {
            for (Map.Entry<Character, List<Neighbour>> entry
                    : topology.entrySet()) {
                db.put(entry.getKey(),
                    new ArrayList<>(entry.getValue()));
                nodes.add(entry.getKey());
            }
        }

        for (List<Neighbour> links : db.values()) {
            for (Neighbour n : links) {
                nodes.add(n.name);
            }
        }

        List<Character> ordered = new ArrayList<>(nodes);

        System.out.println();
        System.out.println("Local topology matrix at VN " + myId + ":");

        for (int i = 0; i < ordered.size() - 1; i++) {
            StringBuilder row = new StringBuilder();

            for (int j = i + 1; j < ordered.size(); j++) {
                char a = ordered.get(i);
                char b = ordered.get(j);

                Integer cost = findCost(db, a, b);

                if (cost == null) {
                    cost = findCost(db, b, a);
                }

                if (j > i + 1) {
                    row.append(' ');
                }

                row.append(cost == null ? -1 : cost);
            }

            System.out.println(row);
        }
    }

    static void startMatrixPrinter() {
        Thread printer = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                printMatrix();

                try {
                    Thread.sleep(6000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        });

        printer.setDaemon(true);
        printer.start();
    }

    ///////////////////////////////
    /// MAIN METHOD STARTS HERE:
    /// 
    public static void main(String[] args) throws IOException {
        if (args.length < 2 || args.length > 3) {
            System.out.println(
                "Usage: java VN <ON-IP> <UDP-port> [local-IPv4]"
            );
            return;
        }

        InetAddress onIP = InetAddress.getByName(args[0]);
        int portNumber = Integer.parseInt(args[1]);

        if (portNumber < 1 || portNumber > 65535) {
            throw new IllegalArgumentException("Invalid UDP port.");
        }

        InetAddress localIP = args.length == 3
            ? InetAddress.getByName(args[2])
            : InetAddress.getLocalHost();

        if (localIP.getAddress().length != 4) {
            throw new IllegalArgumentException(
                "A local IPv4 address is required."
            );
        }

        int localIPInt = ipToInt(localIP);

        try (DatagramSocket udp = new DatagramSocket(portNumber);
             Socket socket = new Socket(onIP, 6000)) {

            udpSocket = udp;

            ByteBuffer connect = ByteBuffer.allocate(6);
            connect.put(localIP.getAddress());
            connect.putShort((short) portNumber);

            DataOutputStream out = new DataOutputStream(
                socket.getOutputStream()
            );
            DataInputStream in = new DataInputStream(
                socket.getInputStream()
            );

            out.write(connect.array());
            out.flush();

            handleONUpdate(readLinks(in), localIPInt, portNumber);

            Thread receiver = new Thread(VN::receiveLSP);
            receiver.setDaemon(true);
            receiver.start();

            startMatrixPrinter();

            while (true) {
                handleONUpdate(readLinks(in), localIPInt, portNumber);
            }

        } catch (EOFException e) {
            System.out.println("ON closed the connection.");
        }
    }
}
