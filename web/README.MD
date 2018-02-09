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
