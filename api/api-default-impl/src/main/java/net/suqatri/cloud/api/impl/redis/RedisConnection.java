package net.suqatri.cloud.api.impl.redis;

import net.suqatri.cloud.api.CloudAPI;
import net.suqatri.cloud.api.impl.redis.bucket.packet.BucketUpdatePacket;
import net.suqatri.cloud.api.redis.IRedisConnection;
import net.suqatri.cloud.api.redis.RedisCredentials;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;

public class RedisConnection implements IRedisConnection {

    private RedisCredentials redisCredentials;
    private RedissonClient client;

    public RedisConnection(RedisCredentials redisCredentials) {
        this.redisCredentials = redisCredentials;
        this.registerPackets();
    }

    private void registerPackets(){
        CloudAPI.getInstance().getPacketManager().registerPacket(BucketUpdatePacket.class);
    }

    @Override
    public RedisCredentials getCredentials() {
        return this.redisCredentials;
    }

    @Override
    public void connect() {
        Config config = new Config();
        config.useSingleServer()
                .setAddress("redis://" + redisCredentials.getHostname() + ":" + redisCredentials.getPort())
                .setPassword(this.redisCredentials.getPassword())
                .setDatabase(this.redisCredentials.getDatabaseId());
        this.client = Redisson.create(config);
    }

    @Override
    public void disconnect() {
        this.client.shutdown();
    }

    @Override
    public boolean isConnected() {
        if(this.client == null) return false;
        return !this.client.isShutdown();
    }

    @Override
    public void reconnect() {
        this.disconnect();
        this.connect();
    }

    @Override
    public RedissonClient getClient() {
        return this.client;
    }
}