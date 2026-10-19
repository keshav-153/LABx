
import java.io.*;
import java.nio.ByteBuffer;
import java.net.*;
import java.util.Arrays;
import java.util.List;
import java.util.ArrayList;

class VirtualNode {
    int IPaddress, UDPport;
    public VirtualNode (int ip, int udp) {
        IPaddress = ip;
        UDPport = udp;
    }
}

class Neighbour extends VirtualNode{
    int cost;
    char name;

    public Neighbour (int ip, int udp, int c, char n) {
        super(ip, udp);
        cost = c;
        name = n;
    }
}

public class VN {
    public static void main(String[] args) throws IOException {
        InetAddress localIp = InetAddress.getLocalHost();
        int portNumber = Integer.parseInt(args[0]);

        Socket socket = new Socket(localIp, 5000);
        DatagramSocket udpSocket = new DatagramSocket(portNumber);

        byte[] ipBytes = localIp.getAddress();

        ByteBuffer sendbuffer = ByteBuffer.allocate(6);
        sendbuffer.put(ipBytes);
        sendbuffer.putShort((short) portNumber);

        OutputStream out = socket.getOutputStream();
        DataInputStream din = new DataInputStream(socket.getInputStream());

        out.write(sendbuffer.array());
        out.flush();

        while (true) {
            int count;

            try {
                count = din.readUnsignedShort();
            } catch (EOFException e) {
                break;
            }

            List<Neighbour> neighbours = new ArrayList<>();

            for (int i = 0; i < count; i++) {
                char name = (char) din.readUnsignedByte();
                int neighbourIP = din.readInt();
                int neighbourPort = din.readUnsignedShort();
                int cost = din.readUnsignedByte();

                Neighbour neighbour = new Neighbour(
                    neighbourIP, neighbourPort, cost, name
                );
                neighbours.add(neighbour);

                System.out.println("Neighbour: " + neighbour.name + ", IP: " + neighbour.IPaddress + ", Port: " + neighbour.UDPport + ", Cost: " + neighbour.cost);
            }
        }

        socket.close();
        udpSocket.close();
    }
}
