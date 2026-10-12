import java.io.*;
import java.net.*;

public class Server {
    public static void main(String[] args) throws IOException {
        ServerSocket serverSocket = new ServerSocket(12345);
        System.out.println("Server is listening...");

        Socket socket = serverSocket.accept();
        BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        PrintWriter writer = new PrintWriter(socket.getOutputStream(), true);

        String message = reader.readLine();
        System.out.println("Received message: " + message);

        writer.println("Acknowledgment: Message received!");

        socket.close();
        serverSocket.close();
    }
}