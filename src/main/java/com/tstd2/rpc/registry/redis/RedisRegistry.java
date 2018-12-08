package com.tstd2.rpc.registry.redis;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.tstd2.rpc.configBean.Protocol;
import com.tstd2.rpc.configBean.Registry;
import com.tstd2.rpc.configBean.Service;
import com.tstd2.rpc.registry.BaseRegistry;
import com.tstd2.rpc.registry.RegistryNode;
import org.springframework.context.ApplicationContext;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

import java.util.*;

/**
 * redis注册中心处理类
 */
public class RedisRegistry implements BaseRegistry {

    private static final Gson GSON = new GsonBuilder().create();

    private RedisClient redisClient;

    @Override
    public boolean registry(String interfaceName, ApplicationContext application) {
        try {
            Protocol protocol = application.getBean(Protocol.class);
            Map<String, Service> services = application.getBeansOfType(Service.class);

            Registry registry = application.getBean(Registry.class);
            this.createRedisPool(registry.getAddress());

            for (Map.Entry<String, Service> entry : services.entrySet()) {
                if (entry.getValue().getInf().equals(interfaceName)) {
                    RegistryNode node = new RegistryNode();
                    node.setProtocol(protocol);
                    node.setService(entry.getValue());

                    this.sadd(interfaceName, node);
                }
            }

            return true;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    private void sadd(String interfaceName, RegistryNode registryNode) {
        if (redisClient.exists(interfaceName)) {

            String host = registryNode.getProtocol().getHost();
            String port = registryNode.getProtocol().getPort();

            Set<String> nodeSet = this.redisClient.smembers(interfaceName);

            boolean isold = false;

            for (String nodeJson : nodeSet) {
                RegistryNode node = GSON.fromJson(nodeJson, RegistryNode.class);

                // 是有有相同的机器
                if (host.equals(node.getProtocol().getHost()) && port.equals(node.getProtocol().getPort())) {
                    isold = true;
                    break;
                }
            }

            if (isold) {
                // 存在则更新
                this.redisClient.del(interfaceName);
                this.redisClient.sadd(interfaceName, GSON.toJson(registryNode));
            } else {
                // 新加入的机器
                this.redisClient.sadd(interfaceName, GSON.toJson(registryNode));
            }
        } else {
            // 第一次加入
            this.redisClient.sadd(interfaceName, GSON.toJson(registryNode));
        }
    }

    /**
     * 初始化redis
     *
     * @param address
     */
    private synchronized void createRedisPool(String address) {

        if (this.redisClient != null) {
            return;
        }

        RedisClient redisClient = new RedisClient();
        // 数据库链接池配置
        JedisPoolConfig config = new JedisPoolConfig();
        config.setMaxTotal(100);
        config.setMaxIdle(50);
        config.setMinIdle(20);
        config.setMaxWaitMillis(6 * 1000);
        config.setTestOnBorrow(true);

        String[] addrs = address.split(":");
        JedisPool jedisPool = new JedisPool(config, addrs[0], Integer.valueOf(addrs[1]), 5000);
        redisClient.setJedisPool(jedisPool);

        this.redisClient = redisClient;
    }

    @Override
    public List<RegistryNode> getRegistry(String interfaceName, ApplicationContext application) {
        Registry registry = application.getBean(Registry.class);
        this.createRedisPool(registry.getAddress());
        if (this.redisClient.exists(interfaceName)) {
            Set<String> set = this.redisClient.smembers(interfaceName);
            List<RegistryNode> nodeList = new ArrayList<>();
            for (String str : set) {
                nodeList.add(GSON.fromJson(str, RegistryNode.class));
            }
            return nodeList;
        }
        return null;
    }

}
