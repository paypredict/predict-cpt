# Install Predict CPT on Ubuntu

## DigitalOcean Setup
 * [Initial Server Setup with Ubuntu 16.04](https://www.digitalocean.com/community/tutorials/initial-server-setup-with-ubuntu-16-04)
 * [Install Apache Tomcat 8 on Ubuntu 16.04](https://www.digitalocean.com/community/tutorials/how-to-install-apache-tomcat-8-on-ubuntu-16-04)
 * [Install R on Ubuntu 16.04](https://www.digitalocean.com/community/tutorials/how-to-install-r-on-ubuntu-16-04-2)

**/etc/systemd/system/tomcat.service**
```
[Unit]
Description=Apache Tomcat Web Application Container
After=network.target

[Service]
Type=forking

Environment=JAVA_HOME=/usr/lib/jvm/java-1.8.0-openjdk-amd64/jre
Environment=CATALINA_PID=/opt/tomcat/temp/tomcat.pid
Environment=CATALINA_HOME=/opt/tomcat
Environment=CATALINA_BASE=/opt/tomcat
Environment='CATALINA_OPTS=-Xms512M -Xmx1024M -server -XX:+UseParallelGC -Djava.net.preferIPv4Stack=true'
Environment='JAVA_OPTS=-Djava.awt.headless=true -Djava.security.egd=file:/dev/./urandom'

ExecStart=/opt/tomcat/bin/startup.sh
ExecStop=/opt/tomcat/bin/shutdown.sh

User=tomcat
Group=tomcat
UMask=0007
RestartSec=10
Restart=always

[Install]
WantedBy=multi-user.target
```

**/etc/ufw/sysctl.conf**
```
# Uncomment this to allow this host to route packets between interfaces
net/ipv4/ip_forward=1
```

**/etc/ufw/before.rules**
```
# Forwarding http tomcat ports
*nat
:PREROUTING ACCEPT [0:0]
:POSTROUTING ACCEPT [0:0]
-A PREROUTING -i eth0 -p tcp --dport 80 -j REDIRECT --to-port 8080
-A PREROUTING -i eth0 -p tcp --dport 443 -j REDIRECT --to-port 8443
COMMIT
```


# letsencrypt
 1. install certbot: https://certbot.eff.org/#ubuntuxenial-other
    ```bash
    $ sudo apt-get update
    $ sudo apt-get install software-properties-common
    $ sudo add-apt-repository ppa:certbot/certbot
    $ sudo apt-get update
    $ sudo apt-get install certbot
    ```
 2. https://community.letsencrypt.org/t/using-lets-encrypt-with-tomcat/41082
    generate ssl/bundle.pfx 
    ```bash
    mkdir /opt/tomcat/ssl
    cd /etc/letsencrypt/live/yourdomain.com
    openssl pkcs12 -export -out /opt/tomcat/ssl/bundle.pfx -inkey privkey.pem -in cert.pem -certfile chain.pem -password pass:apassword
    ```
    add ssl connector to `/opt/tomcat/conf/server.xml`: 
    ```xml
    <Connector
           protocol="org.apache.coyote.http11.Http11NioProtocol"
           port="8443" maxThreads="200"
           scheme="https" secure="true" SSLEnabled="true"
           keystoreFile="ssl/bundle.pfx"
           keystorePass="apassword"
           clientAuth="false" sslProtocol="TLS"/>
    ```
 

# UFW and iptables issue with port forwarding
https://www.digitalocean.com/community/questions/ufw-and-iptables-issue-with-port-forwarding

UFW is a simple frontend for IPtables, and does not allow you to configure everything that IPtables does. Though it does make many common task easier. If you have more complex firewall rules you need to setup, you can use the files:

```
/etc/ufw/before.rules
/etc/ufw/after.rules
```

Placing IPtables rules in `before.rules` will apply those rules before starting UFW. Likewise, rules placed in `after.rules` will be applied after UFW has started in case the order matters.

There are also the files:
```
/etc/ufw/after.init
/etc/ufw/before.init
```
They behave similarly, except that you can execute any arbitrary script rather than just IPtables rules.



# Redirect 443,80 to 8443,8080 on ubuntu with persistence
https://gist.github.com/danibram/d00ed812f2ca6a68758e

**iptables80.sh**
```bash
#!/usr/bin/env bash

sudo iptables -A PREROUTING -t nat -i eth0 -p tcp --dport 80 -j REDIRECT --to-port 8080
sudo iptables -A PREROUTING -t nat -i eth0 -p tcp --dport 443 -j REDIRECT --to-port 8443
sudo sh -c "iptables-save > /etc/iptables.rules"

# sudo apt-get install  iptables-persistent

# allows localhost traffic to connect to 443 from 8443
# sudo iptables -t nat -A OUTPUT -o lo -p tcp --dport 443 -j REDIRECT --to-port 8443
```
