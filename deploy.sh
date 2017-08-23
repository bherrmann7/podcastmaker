lein uberjar
scp target/podcastmaker-0.0.1-SNAPSHOT-standalone.jar jadn.com:pm.jar
ssh jadn.com ./restart-pm.sh

