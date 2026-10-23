
import socket
import threading
import sys

if len(sys.argv) != 2:
    print(f"Usage: python3 {sys.argv[0]} <local_port>")
    sys.exit(1)

local_port = int(sys.argv[1])

sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
sock.bind(("0.0.0.0", local_port))

print(f"Listening on UDP port {local_port}")
print("Type: <destination_ip> <destination_port> <message>")
print("Example: 192.168.43.11 6003 hello from Linux")

def receive():
    while True:
        try:
            data, addr = sock.recvfrom(65535)
            print(f"\n[RECV] {addr[0]}:{addr[1]} -> "
                  f"local port {local_port}: {data.decode(errors='replace')}")
        except OSError:
            break

threading.Thread(target=receive, daemon=True).start()

try:
    while True:
        line = input("> ")
        parts = line.split(maxsplit=2)
        if len(parts) != 3:
            print("Format: <destination_ip> <destination_port> <message>")
            continue

        dest_ip, dest_port, message = parts
        sock.sendto(message.encode(), (dest_ip, int(dest_port)))
        print(f"[SENT] to {dest_ip}:{dest_port}")
except (KeyboardInterrupt, EOFError):
    pass
finally:
    sock.close()
