import socket

def run_server():
    server_socket = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    server_socket.bind(('127.0.0.1', 12345))
    server_socket.listen(1)
    print("listening for connections...")
    
    conn, addr = server_socket.accept()
    print(f"Connected to client: {addr}")
    
    data = conn.recv(1024).decode('utf-8')
    print(f"Received message: {data}")
    
    conn.sendall(b"Acknowledgment: Message received!")
    conn.close()
    server_socket.close()

if __name__ == "__main__":
    run_server()