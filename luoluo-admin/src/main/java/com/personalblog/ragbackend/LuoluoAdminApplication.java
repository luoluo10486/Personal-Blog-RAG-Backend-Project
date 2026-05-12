package com.personalblog.ragbackend;

import org.mybatis.spring.annotation.MapperScan;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.metrics.buffering.BufferingApplicationStartup;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Environment;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.time.Duration;
import java.util.Enumeration;

/**
 * 后台管理端启动入口。
 */
@SpringBootApplication(scanBasePackages = "com.personalblog.ragbackend")
@ConfigurationPropertiesScan(basePackages = {
        "com.personalblog.ragbackend.knowledge.config",
        "com.personalblog.ragbackend.rag.config",
        "com.personalblog.ragbackend.member.config"
})
@MapperScan({
        "com.personalblog.ragbackend.member.mapper",
        "com.personalblog.ragbackend.common.auth.mapper",
        "com.personalblog.ragbackend.knowledge.mapper"
})
public class LuoluoAdminApplication {
    private static final Logger log = LoggerFactory.getLogger(LuoluoAdminApplication.class);

    /**
     * 启动 Spring Boot 应用。
     *
     * @param args 启动参数
     */
    public static void main(String[] args) throws SocketException {
        long startTime = System.currentTimeMillis();
        SpringApplication application = new SpringApplication(LuoluoAdminApplication.class);
        application.setApplicationStartup(new BufferingApplicationStartup(2048));

        ConfigurableApplicationContext context = application.run(args);
        Environment env = context.getEnvironment();
        logStartupSummary(env, startTime);
    }

    private static void logStartupSummary(Environment env, long startTime) throws SocketException {
        String ip = getLocalIp();
        String port = env.getProperty("server.port", "8080");
        String path = env.getProperty("server.servlet.context-path", "");
        String appName = env.getProperty("spring.application.name", "luoluo-admin");
        String profile = env.getProperty("spring.profiles.active", "default");
        String accessPath = normalizeAccessPath(path);
        String baseUrl = "http://localhost:" + port + accessPath;
        String externalBaseUrl = "http://" + ip + ":" + port + accessPath;
        String startupDuration = formatDuration(System.currentTimeMillis() - startTime);

        String vectorType = env.getProperty("app.knowledge.vector.type", "pg");
        String pgSchema = env.getProperty("app.knowledge.vector.pg.schema", "public");
        String pgTable = env.getProperty("app.knowledge.vector.pg.table-name", "t_knowledge_vector");

        log.info("\n\n" +
                        "======================================================================\n" +
                        " Luoluo Admin Started\n" +
                        "======================================================================\n" +
                        "  Application:      {}\n" +
                        "  Profile:          {}\n" +
                        "  Startup Time:     {}\n" +
                        "----------------------------------------------------------------------\n" +
                        "  Backend Local:    {}\n" +
                        "  Backend IP:       {}\n" +
                        "  Login API:        {}/auth/login\n" +
                        "----------------------------------------------------------------------\n" +
                        "  Vector Type:      {}\n" +
                        "  Vector Table:     {}.{}\n" +
                        "======================================================================\n",
                appName,
                profile,
                startupDuration,
                baseUrl,
                externalBaseUrl,
                baseUrl,
                vectorType,
                pgSchema,
                pgTable
        );
    }

    private static String getLocalIp() throws SocketException {
        String candidate = null;
        Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
        while (interfaces.hasMoreElements()) {
            NetworkInterface networkInterface = interfaces.nextElement();
            if (!networkInterface.isUp()
                    || networkInterface.isLoopback()
                    || networkInterface.isVirtual()
                    || networkInterface.isPointToPoint()) {
                continue;
            }

            Enumeration<InetAddress> addresses = networkInterface.getInetAddresses();
            while (addresses.hasMoreElements()) {
                InetAddress address = addresses.nextElement();
                if (!(address instanceof Inet4Address) || address.isLoopbackAddress()) {
                    continue;
                }

                String ip = address.getHostAddress();
                if (ip.startsWith("0.") || ip.startsWith("127.")) {
                    continue;
                }
                if (address.isSiteLocalAddress()) {
                    return ip;
                }
                if (candidate == null) {
                    candidate = ip;
                }
            }
        }
        return candidate == null ? "127.0.0.1" : candidate;
    }

    private static String normalizeAccessPath(String path) {
        if (path == null || path.isBlank() || "/".equals(path)) {
            return "";
        }
        return path.startsWith("/") ? path : "/" + path;
    }

    private static String formatDuration(long millis) {
        Duration duration = Duration.ofMillis(millis);
        long seconds = duration.toSeconds();
        long ms = duration.toMillisPart();
        if (seconds > 0) {
            return seconds + "." + String.format("%03d", ms) + " s";
        }
        return ms + " ms";
    }
}
