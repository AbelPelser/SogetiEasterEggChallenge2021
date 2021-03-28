set -ev
java -D"log4j.configuration=file:./log4j.properties" -cp ../client/target/BlockchainClient.jar client.egg.EggClient ./client_settings.json $@
