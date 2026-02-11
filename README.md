# ozone-AI

## Starting Ozone Docker with UCX capability over Mac

### Docker creation and running
UCX native library is required for UCX env to communicate

1. Build docker image with the Dockerfile
   
   `docker build -t ucx-mac-rdma .`
2. configure .env in compose/ozone (hadoop-ozone/dist/target/compose/ozone/.env) OR other varition of docker compose .env
   ```
   OZONE_RUNNER_VERSION=latest
   OZONE_RUNNER_IMAGE=ucx-mac-rdma
   ```
3. Start docker env via compose


### Java dependency

For compile UCX support client server, need below pom depenency,
```
    <dependency>
      <groupId>org.openucx</groupId>
      <artifactId>jucx</artifactId>
      <version>1.19.1</version>
    </dependency>
```

For Mac runtime, needed below aarch version java jar:

```
<dependency>
      <groupId>org.openucx</groupId>
      <artifactId>jucx</artifactId>
      <version>1.19.1-aarch64</version>
</dependency>
```

### env varialbe to choose UCX over same machine docker

```
export UCX_NET_DEVICES=lo  // using lo as loopback NIC
export UCX_TLS=sm,tcp,self  // choice of protocol
export UCX_MAX_RNDV_RAILS=1  // limit number of network to 1
```
