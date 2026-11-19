package pulse.module;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.Locale;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.client.MinecraftClient;
import net.fabricmc.loader.api.FabricLoader;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/** Server-scoped feature reporting and enforcement for HolyWorld's review API. */
public final class HolyWorldFeatureControl {
   private static final Logger LOGGER = LogManager.getLogger("Fendel/HolyWorld");
   private static final String CLIENT_ID = "fendel-visual";
   private static final long REQUEST_INTERVAL_MS = 10_000L;
   private static final long RESPONSE_TIMEOUT_MS = 5_000L;
   private static final Set<String> BLOCKED_IDS = new LinkedHashSet<>();
   private static final Map<String, Boolean> PRE_JOIN_ENABLED = new LinkedHashMap<>();
   private static volatile String pendingRequestId;
   private static volatile boolean pendingCheck;
   private static long pendingDeadline;
   private static long lastRequestAt;

   private HolyWorldFeatureControl() {}

   public static void initialize() {
      PayloadTypeRegistry.playS2C().register(HolyWorldFeaturePayload.ID, HolyWorldFeaturePayload.CODEC);
      PayloadTypeRegistry.playC2S().register(HolyWorldFeaturePayload.ID, HolyWorldFeaturePayload.CODEC);

      ClientPlayNetworking.registerGlobalReceiver(HolyWorldFeaturePayload.ID, (payload, context) -> {
         String requestId = pendingRequestId;
         if (requestId == null) {
            LOGGER.warn("Received feature-control payload without a pending check; ignoring it.");
            return;
         }
         try {
            JsonObject response = JsonParser.parseString(new String(payload.data(), StandardCharsets.UTF_8)).getAsJsonObject();
            if (!response.has("id") || !requestId.equals(response.get("id").getAsString())) {
               LOGGER.warn("Ignoring feature-control response with a mismatched request ID.");
               return;
            }
            if (!response.has("ok") || !response.get("ok").getAsBoolean()) {
               LOGGER.error("HolyWorld feature-control request failed: {} ({})",
                  response.has("error") ? response.get("error").getAsString() : "MISSING_STATUS",
                  response.has("message") ? response.get("message").getAsString() : "no details");
               return;
            }
            JsonElement payloadElement = response.get("payload");
            if (payloadElement == null || !payloadElement.isJsonObject()) return;
            JsonElement blocklistElement = payloadElement.getAsJsonObject().get("blocklist");
            if (blocklistElement == null || !blocklistElement.isJsonArray()) {
               LOGGER.error("HolyWorld returned a successful feature-control response without a blocklist; keeping modules hidden.");
               return;
            }

            Set<String> blocked = new LinkedHashSet<>();
            for (JsonElement item : blocklistElement.getAsJsonArray()) {
               if (item.isJsonPrimitive() && item.getAsJsonPrimitive().isString()) blocked.add(item.getAsString());
            }
            context.client().execute(() -> applyResponse(requestId, blocked));
         } catch (RuntimeException exception) {
            LOGGER.error("Could not parse the HolyWorld feature-control response; keeping modules hidden.", exception);
         }
      });

      ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
         if (!ClientPlayNetworking.canSend(HolyWorldFeaturePayload.ID)) {
            LOGGER.warn("Server does not advertise liteapi:feature-control; no HolyWorld blocklist can be applied.");
            beginCheck();
            pendingCheck = false;
            LOGGER.error("HolyWorld feature control is unavailable; hiding all client features until disconnect.");
            return;
         }
         beginCheck();
         LOGGER.info("Checking {} feature IDs for client '{}' with HolyWorld.", ModuleRegistry.all().size(), CLIENT_ID);
         trySendCheck();
      });

      ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> restoreAfterDisconnect());
      ClientTickEvents.END_CLIENT_TICK.register(HolyWorldFeatureControl::tick);
   }

   public static String featureId(ClientModule module) {
      String className = module.getClass().getSimpleName();
      return className.replaceAll("([A-Z]+)([A-Z][a-z])", "$1-$2")
         .replaceAll("([a-z0-9])([A-Z])", "$1-$2")
         .toLowerCase(Locale.ROOT);
   }

   public static void writeFeatureManifest() {
      JsonObject manifest = new JsonObject();
      manifest.addProperty("client", CLIENT_ID);
      JsonArray features = new JsonArray();
      for (ClientModule module : ModuleRegistry.all()) {
         JsonObject feature = new JsonObject();
         feature.addProperty("id", featureId(module));
         feature.addProperty("name", module.name());
         feature.addProperty("description", module.description());
         features.add(feature);
      }
      manifest.add("features", features);
      Path file = FabricLoader.getInstance().getConfigDir().resolve("fendel-holyworld-features.json");
      try {
         Files.createDirectories(file.getParent());
         Files.writeString(file, new GsonBuilder().setPrettyPrinting().create().toJson(manifest), StandardCharsets.UTF_8);
      } catch (IOException ignored) {
         // Feature control still works if the optional review manifest cannot be written.
      }
   }

   public static boolean isHidden(ClientModule module) {
      String id = featureId(module);
      synchronized (BLOCKED_IDS) {
         return pendingCheck || BLOCKED_IDS.contains(id);
      }
   }

   private static void beginCheck() {
      PRE_JOIN_ENABLED.clear();
      synchronized (BLOCKED_IDS) {
         BLOCKED_IDS.clear();
      }
      for (ClientModule module : ModuleRegistry.all()) {
         String id = featureId(module);
         PRE_JOIN_ENABLED.put(id, module.storedEnabledState());
         synchronized (BLOCKED_IDS) {
            BLOCKED_IDS.add(id);
         }
         module.setEnabledStateQuiet(false);
      }
      pendingCheck = true;
      pendingRequestId = null;
      pendingDeadline = 0L;
   }

   private static void trySendCheck() {
      long now = System.currentTimeMillis();
      if (!pendingCheck || pendingRequestId != null || (lastRequestAt != 0L && now - lastRequestAt < REQUEST_INTERVAL_MS)) return;

      String id = UUID.randomUUID().toString();
      JsonObject payload = new JsonObject();
      payload.addProperty("client", CLIENT_ID);
      JsonArray features = new JsonArray();
      for (ClientModule module : ModuleRegistry.all()) features.add(featureId(module));
      payload.add("features", features);

      JsonObject request = new JsonObject();
      request.addProperty("id", id);
      request.addProperty("method", "checkFeatures");
      request.add("payload", payload);
      try {
         ClientPlayNetworking.send(new HolyWorldFeaturePayload(request.toString().getBytes(StandardCharsets.UTF_8)));
         pendingRequestId = id;
         pendingDeadline = now + RESPONSE_TIMEOUT_MS;
         lastRequestAt = now;
         LOGGER.info("Sent HolyWorld feature check ({}) with {} IDs.", id, features.size());
      } catch (RuntimeException exception) {
         LOGGER.error("Failed to send HolyWorld feature check; modules remain hidden.", exception);
      }
   }

   private static void applyResponse(String requestId, Set<String> blockedIds) {
      if (!requestId.equals(pendingRequestId) || !pendingCheck) return;
      Set<String> known = new LinkedHashSet<>();
      for (ClientModule module : ModuleRegistry.all()) known.add(featureId(module));
      Set<String> unknown = new LinkedHashSet<>(blockedIds);
      unknown.removeAll(known);
      synchronized (BLOCKED_IDS) {
         BLOCKED_IDS.clear();
         for (String id : blockedIds) if (known.contains(id)) BLOCKED_IDS.add(id);
      }
      pendingRequestId = null;
      pendingDeadline = 0L;
      pendingCheck = false;
      LOGGER.info("HolyWorld returned {} blocked IDs; {} match registered Pulse features.", blockedIds.size(), BLOCKED_IDS.size());
      LOGGER.info("HolyWorld blocked feature IDs: {}", blockedIds);
      if (!unknown.isEmpty()) LOGGER.warn("HolyWorld blocklist contains IDs not present in this client: {}", unknown);
      if (blockedIds.isEmpty()) LOGGER.warn("The server returned an empty blocklist for client '{}'; no per-client features were assigned.", CLIENT_ID);
      for (ClientModule module : ModuleRegistry.all()) {
         String id = featureId(module);
         boolean blocked;
         synchronized (BLOCKED_IDS) {
            blocked = BLOCKED_IDS.contains(id);
         }
         module.setEnabledStateQuiet(!blocked && PRE_JOIN_ENABLED.getOrDefault(id, false));
      }
   }


   private static void restoreAfterDisconnect() {
      Set<String> restoreIds;
      synchronized (BLOCKED_IDS) {
         restoreIds = new LinkedHashSet<>(BLOCKED_IDS);
      }
      if (pendingCheck) restoreIds.addAll(PRE_JOIN_ENABLED.keySet());
      pendingCheck = false;
      pendingRequestId = null;
      pendingDeadline = 0L;
      synchronized (BLOCKED_IDS) {
         BLOCKED_IDS.clear();
      }
      restoreSavedEnabledStates(restoreIds);
   }

   private static void restoreSavedEnabledStates(Set<String> onlyIds) {
      if (!PRE_JOIN_ENABLED.isEmpty()) {
         for (ClientModule module : ModuleRegistry.all()) {
            String id = featureId(module);
            if (onlyIds == null || onlyIds.contains(id)) {
               module.setEnabledStateQuiet(PRE_JOIN_ENABLED.getOrDefault(id, false));
            }
         }
         PRE_JOIN_ENABLED.clear();
      }
   }

   private static void tick(MinecraftClient client) {
      long now = System.currentTimeMillis();
      if (pendingCheck && pendingRequestId == null) trySendCheck();
      if (pendingRequestId != null && now >= pendingDeadline) {
         LOGGER.error("HolyWorld feature check timed out; keeping all features hidden until disconnect.");
         pendingRequestId = null;
         pendingDeadline = 0L;
         // Keep all modules hidden until a valid response or disconnect.
      }
   }
}
