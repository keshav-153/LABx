
#include<iostream>
#include<fstream>
#include<string>
#include<vector>
#include<sstream>
#include <cstring>
#include <cstdint>
#include <unistd.h>
#include <sys/select.h>
#include<sys/socket.h>
#include<netinet/in.h>
#include<arpa/inet.h>

using namespace std;
using uc = unsigned char;
using us = unsigned short;
using ui = unsigned int;

class TCP {
public:
    int port, fd;
    sockaddr_in add;

    TCP(int p) {
        port = p;
        fd = socket(AF_INET, SOCK_STREAM, 0);
        add.sin_family = AF_INET;
        add.sin_addr.s_addr = INADDR_ANY;
        add.sin_port = htons(port);
        bind(fd, (struct sockaddr*)&add, sizeof(add));
        listen(fd, 10);
    }
};

class VirtualNode {
public:
    int IPaddress, UDPport, fd;
    char Id;

    VirtualNode(int ip, int udp, char alpha, int FD) {
        IPaddress = ip;
        UDPport = udp;
        Id = alpha;
        fd = FD;
    }
};

class info {
public:
    int N;
    vector<VirtualNode> v;
    vector<vector<pair<char, int>>> adj;
    info(int n) {
        N = n;
        adj.resize(N);
    }
};

int main(int argc, char* argv[]) {
    string file = argv[1];
    ifstream f(file);
    string line;
    int nodes = 1;
    while(getline(f, line)) nodes++;
    cout << nodes << endl;
    info INFO(nodes);

    f.clear();
    f.seekg(0, ios::beg);
    
    for (int i = 0; i < nodes - 1; i++) {
        getline(f, line);
        stringstream ss(line);
        vector<int> costs;
        int cst;
        while (ss >> cst) 
            costs.push_back(cst);

        for (int j = i+1; j < nodes; j++) {
            int cost = costs[j-i-1];
            if (cost != -1) {
                INFO.adj[i].push_back({char('A' + j), cost});
                INFO.adj[j].push_back({char('A' + i), cost});
            }
        }
    }
    
    for (int i = 0; i < nodes; i++) {
        cout << char('A' + i) << ": ";
        for (auto e: INFO.adj[i]) 
            cout << "(" << e.first << ", " << e.second << ") ";
        cout << endl;
    }

    TCP server(5000);
    cout << "ON listening (port 5000)" << endl;
    while (INFO.v.size() < INFO.N) {
        sockaddr_in client;
        socklen_t len = sizeof(client);
        int cfd = accept(server.fd, (struct sockaddr*)&client, &len);
        char msg[6];
        recv(cfd, msg, 6, MSG_WAITALL);
        uint32_t ip;
        memcpy(&ip, msg, 4);
        int udp = (uc)msg[4] * 256 + (uc)msg[5];
        char id = 'A' + INFO.v.size();
        INFO.v.push_back(VirtualNode(ntohl(ip), udp, id, cfd));
        cout << "VN " << id << " connected" << endl;
    }

    for (int i = 0; i < INFO.N; i++) {
        vector<char> msg;
        us count = htons(INFO.adj[i].size() + 1);
        msg.insert(msg.end(), (char*)&count, (char*)&count + 2);
        
        for (int j = 0; j < INFO.N; j++) {
            int cost = -1;
            if (i == j) cost = 0;
            else {
                for (auto e: INFO.adj[i]) 
                    if (e.first == INFO.v[j].Id) {
                        cost = e.second; break;
                    }
            }

            if (cost == -1) continue;
            msg.push_back(INFO.v[j].Id);
            ui ip = htonl(INFO.v[j].IPaddress);
            us udp = htons(INFO.v[j].UDPport);

            msg.insert(msg.end(), (char*)&ip, (char*)&ip + 4);
            msg.insert(msg.end(), (char*)&udp, (char*)&udp + 2);
            msg.push_back((uc)cost);
        }
        send(INFO.v[i].fd, msg.data(), msg.size(), 0);
    }

    while (1) {
        fd_set fds;
        FD_ZERO(&fds);
        FD_SET(server.fd, &fds);
        timeval tv = {0, 0};

        if (select(server.fd + 1, &fds, NULL, NULL, &tv) > 0) {
            sockaddr_in client;
            socklen_t len = sizeof(client);
            int cfd = accept(server.fd, (struct sockaddr*)&client, &len);
            char msg[6];
            recv(cfd, msg, 6, MSG_WAITALL);

            uint32_t ip;
            memcpy(&ip, msg, 4);
            int udp = (uc)msg[4] * 256 + (uc)msg[5];
            char id = 'A' + INFO.v.size();
            INFO.v.push_back(VirtualNode(ntohl(ip), udp, id, cfd));

            vector<char> reply;
            us count = htons(1);
            reply.insert(reply.end(), (char*)&count, (char*)&count + 2);
            reply.push_back(id);

            ui addr = htonl(INFO.v.back().IPaddress);
            us port = htons(INFO.v.back().UDPport);
            reply.insert(reply.end(), (char*)&addr, (char*)&addr + 4);
            reply.insert(reply.end(), (char*)&port, (char*)&port + 2);
            reply.push_back(0);
            send(cfd, reply.data(), reply.size(), 0);
        }

        sleep(1);
        ifstream nf(file);
        vector<vector<int>> rows;

        while (getline(nf, line)) {
            stringstream ss(line);
            vector<int> costs;
            int cst;
            while (ss >> cst) costs.push_back(cst);
            if (!costs.empty()) rows.push_back(costs);
        }
        nf.close();
        vector<vector<pair<char, int>>> ADJ(INFO.N);

        for (int i = 0; i < INFO.N - 1; i++) {
            for (int j = i+1; j < INFO.N; j++) {
                int cost = rows[i][j-i-1];
                if (cost != -1) {
                    ADJ[i].push_back({char('A' + j), cost});
                    ADJ[j].push_back({char('A' + i), cost});
                }
            }
        }

        if (ADJ != INFO.adj) {
            INFO.adj = ADJ;

            for (int i = 0; i < INFO.N; i++) {
                vector<char> msg;
                us count = htons(INFO.adj[i].size() + 1);
                msg.insert(msg.end(), (char*)&count, (char*)&count + 2);
                
                for (int j = 0; j < INFO.N; j++) {
                    int cost = -1;
                    if (i == j) cost = 0;
                    else {
                        for (auto e: INFO.adj[i]) 
                            if (e.first == INFO.v[j].Id) {
                                cost = e.second; break;
                            }
                    }

                    if (cost == -1) continue;
                    msg.push_back(INFO.v[j].Id);
                    ui ip = htonl(INFO.v[j].IPaddress);
                    us udp = htons(INFO.v[j].UDPport);

                    msg.insert(msg.end(), (char*)&ip, (char*)&ip + 4);
                    msg.insert(msg.end(), (char*)&udp, (char*)&udp + 2);
                    msg.push_back((uc)cost);
                }
                send(INFO.v[i].fd, msg.data(), msg.size(), 0);
            }
        }
    }

    return 0;
}
