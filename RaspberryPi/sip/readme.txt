
sudo apt install liblinphone-dev libv4l-dev alsa-utils ffmpeg

sudo iptables -A INPUT -p tcp --dport 5060 -j ACCEPT
sudo iptables -A INPUT -p udp --dport 5060 -j ACCEPT
sudo iptables -A INPUT -p tcp --dport 5062 -j ACCEPT
sudo iptables -A INPUT -p udp --dport 5062 -j ACCEPT
sudo iptables -A INPUT -p udp --dport 10001:11000 -j ACCEPT

#sudo apt install -y iptables-persistent
#sudo sh -c "iptables-save > /etc/iptables/rules.v4"
sudo netfilter-persistent save

compile:
make m32
make m64

service:
sudo systemctl daemon-reload
sudo systemctl enable sip-receiver.service
sudo systemctl start sip-receiver.service
sudo systemctl status sip-receiver.service


