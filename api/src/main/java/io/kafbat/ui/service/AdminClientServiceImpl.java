package io.kafbat.ui.service;

import io.kafbat.ui.config.ClustersProperties;
import io.kafbat.ui.model.KafkaCluster;
import io.kafbat.ui.util.KafkaClientSslPropertiesUtil;
import java.io.Closeable;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Service
@Slf4j
public class AdminClientServiceImpl implements AdminClientService, Closeable {

  private static final int DEFAULT_CLIENT_TIMEOUT_MS = 30_000;

  private static final AtomicLong CLIENT_ID_SEQ = new AtomicLong();

  private final Map<String, ReactiveAdminClient> adminClientCache = new ConcurrentHashMap<>();
  private final int clientTimeout;

  public AdminClientServiceImpl(ClustersProperties clustersProperties) {
    this.clientTimeout = Optional.ofNullable(clustersProperties.getAdminClientTimeout())
        .orElse(DEFAULT_CLIENT_TIMEOUT_MS);
  }

  @Override
  public Mono<ReactiveAdminClient> get(KafkaCluster cluster) {
    return Mono.justOrEmpty(adminClientCache.get(cluster.getName()))
        .switchIfEmpty(createAdminClient(cluster))
        .map(e -> adminClientCache.computeIfAbsent(cluster.getName(), key -> e));
  }

  private Mono<ReactiveAdminClient> createAdminClient(KafkaCluster cluster) {
    return Mono.fromSupplier(() -> {
      Properties properties = new Properties();
      KafkaClientSslPropertiesUtil.addKafkaSslProperties(cluster.getOriginalProperties().getSsl(), properties);
      properties.putAll(cluster.getProperties());
      properties.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, cluster.getBootstrapServers());
      properties.putIfAbsent(AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG, clientTimeout);
      properties.putIfAbsent(
          AdminClientConfig.CLIENT_ID_CONFIG,
          "kafbat-ui-admin-" + Instant.now().getEpochSecond() + "-" + CLIENT_ID_SEQ.incrementAndGet()
      );
      return AdminClient.create(properties);
    }).subscribeOn(Schedulers.boundedElastic())
        .flatMap(ac -> ReactiveAdminClient.create(ac).doOnError(th -> ac.close()))
        .onErrorMap(th -> new IllegalStateException(
            "Error while creating AdminClient for the cluster " + cluster.getName(), th));
  }

  @Override
  public void close() {
    adminClientCache.values().forEach(ReactiveAdminClient::close);
  }
}
