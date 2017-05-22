/etc/init.d/apache2 start
sudo mkdir /run/lock
sudo mkdir /var/log/apache2/
sudo touch /var/log/apache2/{access,error,other_vhosts_access,suexec}.log
sudo chown -R root:adm /var/log/apache2/
sudo chmod -R 750 /var/log/apache2
