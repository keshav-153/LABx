import java.io.*;
import java.nio.ByteBuffer;
import java.net.*;
import java.util.Arrays;

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
        short portNumber = Short.parseShort(args[1]);

        Socket socket = new Socket(localIp.getHostAddress(), portNumber);

        byte[] ipBytes = localIp.getAddress();

        ByteBuffer sendbuffer = ByteBuffer.allocate(6);
        sendbuffer.put(ipBytes);
        sendbuffer.putShort(portNumber);

        OutputStream out = socket.getOutputStream();
        InputStream in = socket.getInputStream();
        ByteArrayOutputStream byteBuffer = new ByteArrayOutputStream();

        out.write(sendbuffer.array());
        out.flush();

        byte[] receivebuffer = new byte[1024];
        int bytesRead;

        while((bytesRead = in.read(receivebuffer)) != -1) {
            byteBuffer.write(receivebuffer, 0, bytesRead);
        }

        socket.close();
    }
}


