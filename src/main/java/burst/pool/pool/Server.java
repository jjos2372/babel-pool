package burst.pool.pool;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.math.BigInteger;
import java.net.SocketException;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

import org.ehcache.Cache;
import org.ehcache.config.builders.CacheConfigurationBuilder;
import org.ehcache.config.builders.CacheManagerBuilder;
import org.ehcache.config.builders.ResourcePoolsBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;

import burst.kit.crypto.BurstCrypto;
import burst.kit.entity.BurstAddress;
import burst.kit.entity.BurstValue;
import burst.kit.entity.response.MiningInfo;
import burst.kit.util.BurstKitUtils;
import burst.pool.Constants;
import burst.pool.miners.Deadline;
import burst.pool.miners.Miner;
import burst.pool.miners.MinerTracker;
import burst.pool.storage.config.PropertyService;
import burst.pool.storage.config.Props;
import burst.pool.storage.persistent.StorageService;
import fi.iki.elonen.NanoHTTPD;

public class Server extends NanoHTTPD {
    private static final Logger logger = LoggerFactory.getLogger(Server.class);

    private static final String[] allowedFileExtensions = new String[]{".html", ".css", ".js", ".png", ".ico"};

    private final StorageService storageService;
    private final PropertyService propertyService;
    private final Pool pool;
    private final Gson gson = BurstKitUtils.buildGson().create();
    private final BurstCrypto burstCrypto = BurstCrypto.getInstance();
    private final Cache<String, String> fileCache;
    
    public Server(StorageService storageService, PropertyService propertyService, Pool pool) {
        super(propertyService.getInt(Props.serverPort));
        this.storageService = storageService;
        this.propertyService = propertyService;
        this.pool = pool;
        this.fileCache = propertyService.getBoolean(Props.siteDisableCache) ? null :
            CacheManagerBuilder.newCacheManagerBuilder()
                .withCache("file", CacheConfigurationBuilder.newCacheConfigurationBuilder(String.class, String.class, ResourcePoolsBuilder.heap(1024*1024)))
                .build(true)
                .getCache("file", String.class, String.class);
    }

    private long getCurrentHeight() {
        MiningInfo miningInfo = pool.getMiningInfo();
        if (miningInfo == null) return 0;
        return miningInfo.getHeight();
    }

    @Override
    public Response serve(IHTTPSession session) {
        try {
            Map<String, String> params = queryToMap(session.getQueryParameterString());
            session.parseBody(new HashMap<>());
            params.putAll(session.getParms());
            if (session.getUri().startsWith("/burst")) {
                return NanoHTTPD.newFixedLengthResponse(Response.Status.OK, "application/json", handleBurstApiCall(session, params));
            } else if (session.getUri().startsWith("/api")) {
                return NanoHTTPD.newFixedLengthResponse(Response.Status.OK, "application/json", handleApiCall(session, params));
            } else {
                return handleCall(session, params);
            }
        } catch (SocketException e) {
            logger.warn("SocketException for {}", session.getRemoteIpAddress());
            return NanoHTTPD.newFixedLengthResponse(e.getMessage());
        } catch (Exception e) {
            logger.warn("Error getting response", e);
            return NanoHTTPD.newFixedLengthResponse(e.getMessage());
        }
    }

    private String handleBurstApiCall(IHTTPSession session, Map<String, String> params) {
        if (session.getMethod().equals(Method.POST) && Objects.equals(params.get("requestType"), "submitNonce")) {
            BigInteger nonce = null;
            try {
                nonce = new BigInteger(params.get("nonce"));
            } catch (Exception ignored) {}
            Submission submission = new Submission(BurstAddress.fromEither(params.get("accountId")), nonce);
            try {
                if (submission.getMiner() == null) {
                    throw new SubmissionException("Account ID not set");
                }
                if (submission.getNonce() == null) {
                    throw new SubmissionException("Nonce not set or invalid");
                }
                
                String userAgent = session.getHeaders().get("user-agent");
                if (userAgent == null) userAgent = "";
                return gson.toJson(new NonceSubmissionResponse("success", pool.checkNewSubmission(submission, userAgent)));
            } catch (SubmissionException e) {
                return gson.toJson(new NonceSubmissionResponse(e.getMessage(), null));
            }
        } else if (Objects.equals(params.get("requestType"), "getMiningInfo")) {
            MiningInfo miningInfo = pool.getMiningInfo();
            if (miningInfo == null) return gson.toJson(JsonNull.INSTANCE);
            JsonObject miningInfoObj = new JsonObject();
            miningInfoObj.addProperty("height", Long.toUnsignedString(miningInfo.getHeight()));
            miningInfoObj.addProperty("generationSignature", burstCrypto.toHexString(miningInfo.getGenerationSignature()));
            miningInfoObj.addProperty("baseTarget", Long.toUnsignedString(miningInfo.getBaseTarget()));
            miningInfoObj.addProperty("averageCommitmentNQT", Long.toUnsignedString(miningInfo.getAverageCommitmentNQT()));
            
            return miningInfoObj.toString();
            // return gson.toJson(new MiningInfoResponse(burstCrypto.toHexString(miningInfo.getGenerationSignature()), miningInfo.getBaseTarget(), miningInfo.getHeight(), miningInfo.getAverageCommitmentNQT()));
        } else {
            return "404 not found";
        }
    }

    private String handleApiCall(IHTTPSession session, Map<String, String> params) {
        
        if (session.getUri().startsWith("/api/getMiners")) {
            JsonArray minersJson = new JsonArray();
            AtomicReference<Double> poolCapacity = new AtomicReference<>(0d);
            storageService.getMinersFiltered()
                    .stream()
                    .sorted(Comparator.comparing(Miner::getSharedCapacity).reversed())
                    .forEach(miner -> {
                        poolCapacity.updateAndGet(v -> v + miner.getTotalCapacity());
                        minersJson.add(minerToJson(miner, false));
                    });
            JsonObject jsonObject = new JsonObject();
            jsonObject.add("miners", minersJson);
            jsonObject.addProperty("explorer", propertyService.getString(Props.siteExplorerURL) + propertyService.getString(Props.siteExplorerAccount));
            jsonObject.addProperty("poolCapacity", poolCapacity.get());
            return jsonObject.toString();
        } else if (session.getUri().startsWith("/api/getMiner/")) {
            BurstAddress minerAddress = BurstAddress.fromEither(session.getUri().substring(14));
            return minerToJson(storageService.getMiner(minerAddress), true).toString();
        } else if (session.getUri().startsWith("/api/getConfig")) {
            JsonObject response = new JsonObject();
            response.addProperty("version", Constants.VERSION);
            response.addProperty("explorer", propertyService.getString(Props.siteExplorerURL) + propertyService.getString(Props.siteExplorerAccount));
            response.addProperty(Props.poolName.getName(), propertyService.getString(Props.poolName));
            response.addProperty("poolAccount", pool.getAccount().getID());
            response.addProperty("poolAccountRS", pool.getAccount().getFullAddress());
            response.addProperty(Props.nAvg.getName(), propertyService.getInt(Props.nAvg));
            response.addProperty(Props.nMin.getName(), propertyService.getInt(Props.nMin));
            response.addProperty(Props.maxDeadline.getName(), propertyService.getLong(Props.maxDeadline));
            response.addProperty(Props.processLag.getName(), propertyService.getInt(Props.processLag));
            response.addProperty(Props.feeRecipient.getName(), propertyService.getBurstAddress(Props.feeRecipient).getID());
            response.addProperty(Props.feeRecipient.getName() + "RS", propertyService.getBurstAddress(Props.feeRecipient).getFullAddress());
            response.addProperty(Props.poolFeePercentage.getName(), propertyService.getFloat(Props.poolFeePercentage));
            response.addProperty(Props.donationRecipient.getName(), propertyService.getBurstAddress(Props.donationRecipient).getID());
            response.addProperty(Props.donationRecipient.getName() + "RS", propertyService.getBurstAddress(Props.donationRecipient).getFullAddress());
            response.addProperty(Props.donationPercent.getName(), propertyService.getInt(Props.donationPercent));
            response.addProperty(Props.winnerRewardPercentage.getName(), propertyService.getFloat(Props.winnerRewardPercentage));
            response.addProperty(Props.defaultMinimumPayout.getName(), propertyService.getFloat(Props.defaultMinimumPayout));
            response.addProperty(Props.minimumMinimumPayout.getName(), propertyService.getFloat(Props.minimumMinimumPayout));
            response.addProperty(Props.minPayoutsPerTransaction.getName(), propertyService.getInt(Props.minPayoutsPerTransaction));
            response.addProperty("transactionFee", pool.getTransactionFee().toUnformattedString());
            return response.toString();
        } else if (session.getUri().startsWith("/api/getCurrentRound")) {
            return pool.getCurrentRoundInfo(gson).toString();
        } else if (session.getUri().startsWith("/api/getTop10Miners")) {
            AtomicReference<Double> othersShare = new AtomicReference<>(1d);
            JsonArray topMiners = new JsonArray();
            storageService.getMinersFiltered().stream()
                    .sorted((m1, m2) -> Double.compare(m2.getShare(), m1.getShare())) // Reverse order - highest to lowest
                    .limit(10)
                    .forEach(miner -> {
                        topMiners.add(minerToJson(miner, false));
                        othersShare.updateAndGet(share -> share - miner.getShare());
                    });
            JsonObject response = new JsonObject();
            response.add("topMiners", topMiners);
            response.addProperty("explorer", propertyService.getString(Props.siteExplorerURL) + propertyService.getString(Props.siteExplorerAccount));
            response.addProperty("othersShare", othersShare.get());
            return response.toString();
        } else if (session.getUri().startsWith("/api/getWonBlocks")) {
            JsonArray wonBlocks = new JsonArray();
            storageService.getWonBlocks(100)
                    .forEach(wonBlock -> {
                        
                        JsonObject wonBlockJson = new JsonObject();
                        wonBlockJson.addProperty("height", wonBlock.getBlockHeight());
                        wonBlockJson.addProperty("id", wonBlock.getBlockId().getID());
                        wonBlockJson.addProperty("generator", wonBlock.getGeneratorId().getID());
                        wonBlockJson.addProperty("generatorRS", wonBlock.getGeneratorId().getFullAddress());
                        Miner miner = storageService.getMiner(wonBlock.getGeneratorId());
                        if (miner!= null && !Objects.equals(miner.getName(), "")) {
                            wonBlockJson.addProperty("name", miner.getName());
                        }
                        wonBlockJson.addProperty("reward", wonBlock.getFullReward().toFormattedString());
                        wonBlockJson.addProperty("poolShare", wonBlock.getPoolShare().toFormattedString());
                        wonBlocks.add(wonBlockJson);
                    });
            JsonObject response = new JsonObject();
            response.add("wonBlocks", wonBlocks);
            response.addProperty("explorer", propertyService.getString(Props.siteExplorerURL) + propertyService.getString(Props.siteExplorerAccount));

            return response.toString();
        } else {
            return "null";
        }
    }

    private Response handleCall(IHTTPSession session, Map<String, String> params) throws IOException {
        String uri = session.getUri();
        if (Objects.equals(uri, "") || Objects.equals(uri, "/")) {
            uri = "/index.html";
        }
        boolean allowedFile = false;
        for (String extension : allowedFileExtensions) {
            if (uri.endsWith(extension)) allowedFile = true;
        }
        if (!allowedFile || uri.contains("../")) {
            return NanoHTTPD.newFixedLengthResponse(Response.Status.FORBIDDEN, "text/html", "<h1>Access Forbidden</h1>");
        }

        if (fileCache != null && fileCache.containsKey(uri)) {
            return NanoHTTPD.newFixedLengthResponse(Response.Status.OK, URLConnection.guessContentTypeFromName(uri), fileCache.get(uri));
        }

        InputStream inputStream = null;
        if (uri.contains("favicon.ico")) {
            inputStream = new FileInputStream(propertyService.getString(Props.siteIconIco));
        } else if (uri.equals("/img/poolIcon.png")) {
            inputStream = new FileInputStream(propertyService.getString(Props.siteIconPng));
        } else {
            File file = new File("./html" + uri);
            if(file.exists() && file.isFile() && file.canRead())
                inputStream = new FileInputStream(file);
        }

        if (inputStream == null) {
            return redirect("/404.html");
        }

        if (uri.contains(".png") || uri.contains(".ico")) {
            return NanoHTTPD.newChunkedResponse(Response.Status.OK, uri.contains(".ico") ? "image/x-icon" : "image/png", inputStream);
        }

        StringWriter stringWriter = new StringWriter(inputStream.available());
        byte[] buffer = new byte[1024*1024];
        int len;
        while ((len = inputStream.read(buffer)) != -1) {
            stringWriter.write(new String(buffer, StandardCharsets.UTF_8), 0, len);
        }
        String response = stringWriter.toString();

        boolean minimize = true;

        if (uri.contains(".png") || uri.contains(".ico")) minimize = false;

        if (minimize) {
            response = response
                    // Minimizing
                    .replace("    ", "")
                    .replace(" + ", "+")
                    .replace(" = ", "=")
                    .replace(" == ", "==")
                    .replace(" === ", "===")
                    .replace("\r", "")
                    .replace("\n", "")
                    /*.replace(" (", "(")
                    .replace(") ", ")")
                    .replace(", ", ",") TODO this minimization is messing up strings */
                    // Replace links TODO strip tags in links
                    .replace("{TITLE}", propertyService.getString(Props.siteTitle))
                    .replace("{PUBLICNODE}", propertyService.getString(Props.siteNodeAddress))
                    .replace("{DISCORD}", propertyService.getString(Props.siteDiscordLink))
                    .replace("{INFO}", propertyService.getString(Props.siteInfo))
                    .replace("{POOL_ACCOUNT}", burstCrypto.getBurstAddressFromPassphrase(propertyService.getString(Props.passphrase)).getFullAddress())
                    .replace("{LAG}", Integer.toString(propertyService.getInt(Props.processLag)))
                    .replace("{MIN_PAYOUT}", BurstValue.fromBurst(propertyService.getFloat(Props.minimumMinimumPayout)).toUnformattedString())
                    .replace("{FAUCET}", propertyService.getString(Props.siteFaucetURL))
                    .replace("{EXPLORER}", propertyService.getString(Props.siteExplorerURL));
        }
        if(fileCache != null) {
            fileCache.put(uri, response);
        }
        return NanoHTTPD.newFixedLengthResponse(Response.Status.OK, URLConnection.guessContentTypeFromName(session.getUri()), response);
    }

    private Response redirect(String redirectTo) {
        Response r = newFixedLengthResponse(Response.Status.REDIRECT, MIME_HTML, "");
        r.addHeader("Location", redirectTo);
        return r;
    }

    private JsonElement minerToJson(Miner miner, boolean returnDeadlines) {
        if (miner == null) return JsonNull.INSTANCE;
        
        MiningInfo miningInfo = pool.getMiningInfo();
        JsonObject minerJson = new JsonObject();
        double commitmentFactor = MinerTracker.getCommitmentFactor(miner.getCommitment(), miningInfo);
        minerJson.addProperty("address", miner.getAddress().getID());
        minerJson.addProperty("addressRS", miner.getAddress().getFullAddress());
        minerJson.addProperty("pendingBalance", miner.getPending().toFormattedString());
        minerJson.addProperty("totalCapacity", miner.getTotalCapacity());
        minerJson.addProperty("commitment", miner.getCommitment().toFormattedString());
        minerJson.addProperty("committedBalance", miner.getCommittedBalance().toFormattedString());
        minerJson.addProperty("commitmentRatio", (double)miner.getCommitment().longValue()/miningInfo.getAverageCommitmentNQT());
        minerJson.addProperty("commitmentFactor", commitmentFactor);
        minerJson.addProperty("sharedCapacity", miner.getSharedCapacity());
        minerJson.addProperty("sharePercent", miner.getSharePercent());
        minerJson.addProperty("donationPercent", miner.getDonationPercent());
        minerJson.addProperty("nConf", miner.getNConf());
        minerJson.addProperty("share", miner.getShare());
        minerJson.addProperty("minimumPayout", miner.getMinimumPayout().toFormattedString());
        BigInteger bestDeadline = miner.getBestDeadline(getCurrentHeight());
        if (bestDeadline != null) {
        	BigInteger deadline = bestDeadline;
       		deadline = BigInteger.valueOf((long)(Math.log(deadline.doubleValue()/commitmentFactor) * Pool.LN_FACTOR));

            minerJson.addProperty("currentRoundBestDeadline", deadline.toString());
        }
        if (!Objects.equals(miner.getName(), "")) {
            minerJson.addProperty("name", miner.getName());
        }
        if (!Objects.equals(miner.getUserAgent(), "")) {
            minerJson.addProperty("userAgent", miner.getUserAgent());
        }
        
        if(returnDeadlines) {
            List<Deadline> deadlines = miner.getDeadlines();
            JsonArray deadlinesJson = new JsonArray();
            JsonArray sharesJson = new JsonArray();
            for(Deadline d : deadlines) {
                deadlinesJson.add(d.getDeadline().longValue());
                sharesJson.add(d.getSharePercent());
            }
            minerJson.add("deadlines", deadlinesJson);
            minerJson.add("shares", sharesJson);
        }
        
        return minerJson;
    }

    private static Map<String, String> queryToMap(String query) {
        Map<String, String> result = new HashMap<>();
        if (query == null) return result;
        for (String param : query.split("&")) {
            String[] entry = param.split("=");
            if (entry.length > 1) {
                result.put(entry[0], entry[1]);
            } else {
                result.put(entry[0], "");
            }
        }
        return result;
    }
}
