import socket

client_socket = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
client_socket.connect(('127.0.0.1', 12345))
message = "I am client"
client_socket.sendall(message.encode('utf-8'))
ack = client_socket.recv(1024).decode('utf-8')
print(f"Server response: {ack}")
client_socket.close()