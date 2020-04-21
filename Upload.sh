rsync --remove-source-files -av -e "ssh -p 8100" target/decentralized-orchestration-for-edge-computing-1.0-SNAPSHOT.jar mihael@88.200.63.133:app.jar
rsync -av -e "ssh -p 8100" config.json mihael@88.200.63.133:
rsync -av -e "ssh -p 8100" vdf-cli mihael@88.200.63.133: