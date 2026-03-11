package net.hytaledepot.templates.mod.commands;

import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public final class CommandsModTemplate {
  private final Map<String, AtomicLong> actionCounters = new ConcurrentHashMap<>();
  private final Map<String, String> lastActionBySender = new ConcurrentHashMap<>();
  private final AtomicBoolean demoFlagEnabled = new AtomicBoolean(false);
  private final AtomicLong errorCount = new AtomicLong();
  private final Map<String, String> domainState = new ConcurrentHashMap<>();
  private final Map<String, AtomicLong> numericState = new ConcurrentHashMap<>();

  private volatile Path dataDirectory;

  public void onInitialize(Path dataDirectory) {
    this.dataDirectory = dataDirectory;
    actionCounters.clear();
    lastActionBySender.clear();
    domainState.clear();
    numericState.clear();
  }

  public void onShutdown() {
    actionCounters.clear();
    lastActionBySender.clear();
    domainState.clear();
    numericState.clear();
  }

  public void onHeartbeat(long tick) {
    actionCounters.computeIfAbsent("heartbeat", key -> new AtomicLong()).incrementAndGet();
    if (tick % 90 == 0) {
      actionCounters.computeIfAbsent("milestone", key -> new AtomicLong()).incrementAndGet();
    }
  }

  public String runAction(String sender, String action, long heartbeatTicks) {
    String normalizedSender = String.valueOf(sender == null ? "unknown" : sender);
    String normalizedAction = normalizeAction(action);

    actionCounters.computeIfAbsent(normalizedAction, key -> new AtomicLong()).incrementAndGet();
    lastActionBySender.put(normalizedSender, normalizedAction);

    if ("toggle".equals(normalizedAction)) {
      boolean enabled = toggleFlag(demoFlagEnabled);
      return "[CommandsMod] demoFlag=" + enabled + ", heartbeatTicks=" + heartbeatTicks;
    }

    if ("info".equals(normalizedAction)) {
      return "[CommandsMod] " + diagnostics(normalizedSender, heartbeatTicks);
    }

    String domainResult = handleDomainAction(normalizedSender, normalizedAction, heartbeatTicks);
    if (domainResult != null) {
      return "[CommandsMod] " + domainResult;
    }

    return "[CommandsMod] unknown action='" + normalizedAction + "' (try: info, toggle, sample, alias-check, register-alias, resolve-alias)";
  }

  public String diagnostics(String sender, long heartbeatTicks) {
    String directory = dataDirectory == null ? "unset" : dataDirectory.toString();
    return "sender="
        + sender
        + ", heartbeatTicks="
        + heartbeatTicks
        + ", demoFlag="
        + demoFlagEnabled.get()
        + ", ops="
        + operationCount()
        + ", lastAction="
        + lastActionBySender.getOrDefault(sender, "none")
        + ", errors="
        + errorCount.get()
        + ", domainEntries="
        + domainState.size()
        + ", numericEntries="
        + numericState.size()
        + ", dataDirectory="
        + directory;
  }

  public long operationCount() {
    long total = 0;
    for (AtomicLong value : actionCounters.values()) {
      total += value.get();
    }
    return total;
  }

  public void incrementErrorCount() {
    errorCount.incrementAndGet();
  }

  private String handleDomainAction(String sender, String action, long heartbeatTicks) {
    if ("sample".equals(action) || "alias-check".equals(action)) {
      domainState.put("alias:shop", "market");
      domainState.put("alias:spawn", "hub");
      return "aliasCount=2, shop->" + domainState.get("alias:shop");
    }
    if ("register-alias".equals(action)) {
      String aliasKey = "alias:user:" + sender.toLowerCase();
      domainState.put(aliasKey, "home");
      return "registered " + aliasKey + " -> home";
    }
    if ("resolve-alias".equals(action)) {
      String aliasKey = "alias:user:" + sender.toLowerCase();
      return aliasKey + " -> " + domainState.getOrDefault(aliasKey, "<missing>");
    }
    return null;
  }

  private long incrementNumber(String key, long delta) {
    return numericState.computeIfAbsent(key, item -> new AtomicLong()).addAndGet(delta);
  }

  private long number(String key) {
    return numericState.computeIfAbsent(key, item -> new AtomicLong()).get();
  }

  private void setNumber(String key, long value) {
    numericState.computeIfAbsent(key, item -> new AtomicLong()).set(value);
  }

  private static boolean toggleFlag(AtomicBoolean flag) {
    while (true) {
      boolean current = flag.get();
      boolean next = !current;
      if (flag.compareAndSet(current, next)) {
        return next;
      }
    }
  }

  private static String normalizeAction(String action) {
    String normalized = String.valueOf(action == null ? "" : action).trim().toLowerCase();
    return normalized.isEmpty() ? "sample" : normalized;
  }
}
