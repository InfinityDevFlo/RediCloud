package net.suqatri.redicloud.plugin.proxy;

import lombok.Getter;
import lombok.Setter;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.config.ServerInfo;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.api.scheduler.ScheduledTask;
import net.suqatri.redicloud.api.CloudAPI;
import net.suqatri.redicloud.api.impl.CloudDefaultAPIImpl;
import net.suqatri.redicloud.api.impl.player.CloudPlayerManager;
import net.suqatri.redicloud.api.impl.redis.RedisConnection;
import net.suqatri.redicloud.api.impl.service.CloudService;
import net.suqatri.redicloud.api.impl.service.CloudServiceManager;
import net.suqatri.redicloud.api.impl.service.factory.CloudServiceFactory;
import net.suqatri.redicloud.api.impl.service.version.CloudServiceVersionManager;
import net.suqatri.redicloud.api.impl.template.CloudServiceTemplateManager;
import net.suqatri.redicloud.api.network.INetworkComponentInfo;
import net.suqatri.redicloud.api.redis.RedisCredentials;
import net.suqatri.redicloud.api.redis.bucket.IRBucketHolder;
import net.suqatri.redicloud.api.service.ICloudService;
import net.suqatri.redicloud.api.service.ServiceEnvironment;
import net.suqatri.redicloud.api.service.ServiceState;
import net.suqatri.redicloud.api.service.event.CloudServiceStartedEvent;
import net.suqatri.redicloud.api.utils.ApplicationType;
import net.suqatri.redicloud.api.utils.Files;
import net.suqatri.redicloud.commons.file.FileWriter;
import net.suqatri.redicloud.plugin.proxy.command.BungeeCloudCommandManager;
import net.suqatri.redicloud.plugin.proxy.console.ProxyConsole;
import net.suqatri.redicloud.plugin.proxy.listener.*;
import net.suqatri.redicloud.plugin.proxy.scheduler.BungeeScheduler;
import net.suqatri.redicloud.plugin.proxy.service.CloudProxyServiceManager;

import java.io.File;
import java.net.InetSocketAddress;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Getter
public class ProxyCloudAPI extends CloudDefaultAPIImpl<CloudService> {

    @Getter
    private static ProxyCloudAPI instance;

    private final Plugin plugin;
    private final CloudServiceFactory serviceFactory;
    private final CloudProxyServiceManager serviceManager;
    private final CloudServiceVersionManager serviceVersionManager;
    private final CloudServiceTemplateManager serviceTemplateManager;
    private final ProxyConsole console;
    private final BungeeCloudCommandManager commandManager;
    private final BungeeScheduler scheduler;
    private final CloudPlayerManager playerManager;
    private CloudService service;
    private RedisConnection redisConnection;
    private ScheduledTask updaterTask;
    @Setter
    private String chatPrefix = "§bRedi§3Cloud §8» §f";
    @Setter
    private int onlineCount = 0;
    private boolean isShutdownInitiated = false;

    public ProxyCloudAPI(Plugin plugin) {
        super(ApplicationType.SERVICE_PROXY);
        instance = this;
        this.plugin = plugin;
        this.scheduler = new BungeeScheduler(this.plugin);
        this.console = new ProxyConsole(this.plugin.getLogger());
        this.serviceManager = new CloudProxyServiceManager();
        this.serviceFactory = new CloudServiceFactory(this.serviceManager);
        this.serviceVersionManager = new CloudServiceVersionManager();
        this.serviceTemplateManager = new CloudServiceTemplateManager();
        this.commandManager = new BungeeCloudCommandManager(this.plugin);
        this.playerManager = new CloudPlayerManager();


        initRedis();
        registerInternalPackets();
        registerInternalListeners();
        initListeners();
        initThisService();
    }

    private void initListeners() {
        ProxyServer.getInstance().getPluginManager().registerListener(this.plugin, new LoginListener());
        ProxyServer.getInstance().getPluginManager().registerListener(this.plugin, new ProxyPingListener());
        ProxyServer.getInstance().getPluginManager().registerListener(this.plugin, new ServerSwitchListener());
        ProxyServer.getInstance().getPluginManager().registerListener(this.plugin, new PlayerDisconnectListener());
        ProxyServer.getInstance().getPluginManager().registerListener(this.plugin, new ServerKickListener());
        ProxyServer.getInstance().getPluginManager().registerListener(this.plugin, new ServerConnectListener());
        ProxyServer.getInstance().getPluginManager().registerListener(this.plugin, new PostLoginListener());

        getEventManager().register(new CloudServiceStartedListener());
        getEventManager().register(new CloudServiceStoppedListener());
    }

    void registerStartedService() {
        this.serviceManager.getServicesAsync()
                .onFailure(e -> this.console.error("Failed to register started service", e))
                .onSuccess(serviceHolders -> {
                    ProxyServer.getInstance().getServers().clear();

                    for (IRBucketHolder<ICloudService> serviceHolder : serviceHolders) {
                        if (serviceHolder.get().getEnvironment() == ServiceEnvironment.PROXY) continue;
                        ServerInfo serverInfo = ProxyServer.getInstance().constructServerInfo(
                                serviceHolder.get().getServiceName(),
                                new InetSocketAddress(serviceHolder.get().getHostName(), serviceHolder.get().getPort()),
                                serviceHolder.get().getMotd(),
                                false);
                        ProxyServer.getInstance().getServers().put(serverInfo.getName(), serverInfo);
                        CloudAPI.getInstance().getConsole().debug("Registered service: " + serverInfo.getName());
                    }

                    ServerInfo fallback = ProxyServer.getInstance().constructServerInfo(
                            "fallback",
                            new InetSocketAddress("127.0.0.1", 0),
                            "Fallback",
                            false);
                    ProxyServer.getInstance().getServers().put(fallback.getName(), fallback);
                    CloudAPI.getInstance().getConsole().debug("Registered service: " + fallback.getName());

                    ServerInfo lobby = ProxyServer.getInstance().constructServerInfo(
                            "lobby",
                            new InetSocketAddress("127.0.0.1", 0),
                            "lobby",
                            false);
                    ProxyServer.getInstance().getServers().put(lobby.getName(), lobby);
                    CloudAPI.getInstance().getConsole().debug("Registered service: " + lobby.getName());
                });
    }

    private void initThisService() {
        this.service = this.serviceManager.getService(UUID.fromString(System.getenv("redicloud_service_id"))).getImpl(CloudService.class);
        getEventManager().postGlobalAsync(new CloudServiceStartedEvent(this.service.getHolder()));

        this.updaterTask = ProxyServer.getInstance().getScheduler().schedule(this.plugin, () -> {
            if (this.service.getOnlineCount() != this.onlineCount) {
                this.service.setOnlineCount(this.onlineCount);
                this.service.updateAsync();
            }
        }, 1500, 1500, TimeUnit.MILLISECONDS);
    }

    private void initRedis() {
        RedisCredentials redisCredentials;
        try {
            redisCredentials = FileWriter.readObject(new File(System.getenv("redicloud_files_" + Files.REDIS_CONFIG.name().toLowerCase())), RedisCredentials.class);
        } catch (Exception e) {
            this.console.error("Failed to read redis.json file! Please check your credentials.");
            return;
        }
        this.redisConnection = new RedisConnection(redisCredentials);
        try {
            this.redisConnection.connect();
            this.console.info("Redis connection established!");
        } catch (Exception e) {
            this.console.error("§cFailed to connect to redis server. Please check your credentials.", e);
        }
    }

    @Override
    public void updateApplicationProperties(CloudService object) {
        if (!object.getUniqueId().equals(service.getUniqueId())) return;

    }

    @Override
    public INetworkComponentInfo getNetworkComponentInfo() {
        return this.service.getNetworkComponentInfo();
    }

    @Override
    public void shutdown(boolean fromHook) {
        if(this.isShutdownInitiated) return;
        this.isShutdownInitiated = true;

        this.service.setServiceState(ServiceState.STOPPING);
        this.service.update();

        for (ProxiedPlayer onlinePlayer : ProxyServer.getInstance().getPlayers()) {
            onlinePlayer.disconnect("§cServer is shutting down.");
        }

        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }

        if (this.updaterTask != null) this.updaterTask.cancel();

        if (this.redisConnection != null) this.redisConnection.getClient().shutdown();

        ProxyServer.getInstance().stop();
    }
}
