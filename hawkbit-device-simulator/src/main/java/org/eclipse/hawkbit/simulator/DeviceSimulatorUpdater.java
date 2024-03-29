/**
 * Copyright (c) 2015 Bosch Software Innovations GmbH and others
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.hawkbit.simulator;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.security.DigestOutputStream;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.apache.commons.io.IOUtils;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.ssl.SSLContextBuilder;
import org.eclipse.hawkbit.dmf.amqp.api.EventTopic;
import org.eclipse.hawkbit.dmf.json.model.DmfArtifact;
import org.eclipse.hawkbit.dmf.json.model.DmfSoftwareModule;
import org.eclipse.hawkbit.simulator.AbstractSimulatedDevice.Protocol;
import org.eclipse.hawkbit.simulator.UpdateStatus.ResponseStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

/**
 * Update simulation handler.
 */
@Service
public class DeviceSimulatorUpdater {

    private static final Logger LOGGER = LoggerFactory.getLogger(DeviceSimulatorUpdater.class);
    private static final String LOG_PREFIX = "[{}:{}] ";

    @Autowired
    private ScheduledExecutorService threadPool;

    @Autowired
    @Lazy
    private SimulatedDeviceFactory deviceFactory;

    @Autowired
    private DeviceSimulatorRepository repository;

    @Autowired
    private SimulationProperties simulationProperties;

    /**
     * Starting an simulated update process of an simulated device.
     *
     * @param tenant
     *            the tenant of the device
     * @param id
     *            the ID of the simulated device
     * @param modules
     *            the software module version from the hawkbit update server
     * @param targetSecurityToken
     *            the target security token for download authentication
     * @param gatewayToken
     *            as alternative to target token the gateway token for download
     *            authentication
     * @param callback
     *            the callback which gets called when the simulated update
     *            process has been finished
     * @param actionType
     *            indicating whether to download and install or skip
     *            installation due to maintenance window.
     */
    public void startUpdate(final String tenant, final String id, final List<DmfSoftwareModule> modules,
            final String targetSecurityToken, final String gatewayToken, final UpdaterCallback callback,
            final EventTopic actionType) {

        AbstractSimulatedDevice device = repository.get(tenant, id);

        // plug and play - non existing device will be auto created
        if (device == null) {
            device = repository
                    .add(deviceFactory.createSimulatedDevice(id, tenant, Protocol.DMF_AMQP, 1800, null, null));
        }

        device.setTargetSecurityToken(targetSecurityToken);

        threadPool.schedule(new DeviceSimulatorUpdateThread(device, callback, modules, actionType, gatewayToken,
                simulationProperties.isDownloadAuthenticationEnabled()), 2_000, TimeUnit.MILLISECONDS);
    }

    private static final class DeviceSimulatorUpdateThread implements Runnable {

        private static final String BUT_GOT_LOG_MESSAGE = " but got: ";

        private static final String DOWNLOAD_LOG_MESSAGE = "Download ";

        private static final int MINIMUM_TOKENLENGTH_FOR_HINT = 6;

        private final EventTopic actionType;

        private final AbstractSimulatedDevice device;
        private final UpdaterCallback callback;
        private final List<DmfSoftwareModule> modules;
        private final String gatewayToken;
        private final boolean downloadAuthenticationEnabled;

        private DeviceSimulatorUpdateThread(final AbstractSimulatedDevice device, final UpdaterCallback callback,
                final List<DmfSoftwareModule> modules, final EventTopic actionType, final String gatewayToken,
                final boolean downloadAuthenticationEnabled) {
            this.device = device;
            this.callback = callback;
            this.modules = modules;
            this.actionType = actionType;
            this.gatewayToken = gatewayToken;
            this.downloadAuthenticationEnabled = downloadAuthenticationEnabled;
        }

        @Override
        public void run() {
            device.setUpdateStatus(new UpdateStatus(ResponseStatus.RUNNING, "Simulation begins!"));
            callback.sendFeedback(device);

            if (!CollectionUtils.isEmpty(modules)) {
                device.setUpdateStatus(simulateDownloads());
                callback.sendFeedback(device);
                if (isErrorResponse(device.getUpdateStatus())) {
                    device.clean();
                    return;
                }
            }

            if (actionType == EventTopic.DOWNLOAD_AND_INSTALL) {
                device.setUpdateStatus(new UpdateStatus(ResponseStatus.SUCCESSFUL, "Simulation complete!"));
                callback.sendFeedback(device);
                device.clean();
            }
        }

        private UpdateStatus simulateDownloads() {

            device.setUpdateStatus(new UpdateStatus(ResponseStatus.DOWNLOADING,
                    modules.stream().flatMap(mod -> mod.getArtifacts().stream())
                            .map(art -> "Download starts for: " + art.getFilename() + " with SHA1 hash "
                                    + art.getHashes().getSha1() + " and size " + art.getSize())
                            .collect(Collectors.toList())));
            callback.sendFeedback(device);

            final List<UpdateStatus> status = new ArrayList<>();

            LOGGER.info(LOG_PREFIX + "Simulate downloads", device.getTenant(), device.getId());

            modules.forEach(module -> module.getArtifacts().forEach(artifact -> {
                if (downloadAuthenticationEnabled) {
                    handleArtifact(device.getTargetSecurityToken(), gatewayToken, status, artifact);
                } else {
                    handleArtifact(null, null, status, artifact);
                }
            }));

            final UpdateStatus result = new UpdateStatus(ResponseStatus.DOWNLOADED);
            result.getStatusMessages().add("Simulator: Download complete!");
            status.forEach(download -> {
                result.getStatusMessages().addAll(download.getStatusMessages());
                if (isErrorResponse(download)) {
                    result.setResponseStatus(ResponseStatus.ERROR);
                }
            });

            LOGGER.info(LOG_PREFIX + "Download simulations complete", device.getTenant(), device.getId());

            return result;
        }

        private static boolean isErrorResponse(final UpdateStatus status) {
            if (status == null) {
                return false;
            }

            return ResponseStatus.ERROR == status.getResponseStatus();
        }

        private void handleArtifact(final String targetToken, final String gatewayToken,
                final List<UpdateStatus> status, final DmfArtifact artifact) {

            if (artifact.getUrls().containsKey("HTTPS")) {
                status.add(downloadUrl(artifact.getUrls().get("HTTPS"), gatewayToken, targetToken,
                        artifact.getHashes().getSha1(), artifact.getSize()));
            } else if (artifact.getUrls().containsKey("HTTP")) {
                status.add(downloadUrl(artifact.getUrls().get("HTTP"), gatewayToken, targetToken,
                        artifact.getHashes().getSha1(), artifact.getSize()));
            }
        }

        private UpdateStatus downloadUrl(final String url, final String gatewayToken, final String targetToken,
                final String sha1Hash, final long size) {

            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug(LOG_PREFIX + "Downloading {} with token {}, expected sha1 hash {} and size {}", device.getTenant(), device.getId(), url,
                        hideTokenDetails(targetToken), sha1Hash, size);
            }

            try {
                return readAndCheckDownloadUrl(url, gatewayToken, targetToken, sha1Hash, size);
            } catch (IOException | KeyManagementException | NoSuchAlgorithmException | KeyStoreException e) {
                LOGGER.error(LOG_PREFIX + "Failed to download {}", device.getTenant(), device.getId(), url, e);
                return new UpdateStatus(ResponseStatus.ERROR, "Failed to download " + url + ": " + e.getMessage());
            }

        }

        private UpdateStatus readAndCheckDownloadUrl(final String url, final String gatewayToken,
                final String targetToken, final String sha1Hash, final long size)
                throws NoSuchAlgorithmException, KeyStoreException, KeyManagementException, IOException {
            long overallread;
            final CloseableHttpClient httpclient = createHttpClientThatAcceptsAllServerCerts();
            final HttpGet request = new HttpGet(url);

            if (StringUtils.hasLength(targetToken)) {
                request.addHeader(HttpHeaders.AUTHORIZATION, "TargetToken " + targetToken);
            } else if (StringUtils.hasLength(gatewayToken)) {
                request.addHeader(HttpHeaders.AUTHORIZATION, "GatewayToken " + gatewayToken);
            }

            final String sha1HashResult;
            try (final CloseableHttpResponse response = httpclient.execute(request)) {

                if (response.getStatusLine().getStatusCode() != HttpStatus.OK.value()) {
                    final String message = wrongStatusCode(url, response);
                    return new UpdateStatus(ResponseStatus.ERROR, message);
                }

                if (response.getEntity().getContentLength() != size) {
                    final String message = wrongContentLength(url, size, response);
                    return new UpdateStatus(ResponseStatus.ERROR, message);
                }

                // Exception squid:S2070 - not used for hashing sensitive
                // data
                @SuppressWarnings("squid:S2070")
                final MessageDigest md = MessageDigest.getInstance("SHA-1");

                overallread = getOverallRead(response, md);

                if (overallread != size) {
                    final String message = incompleteRead(url, size, overallread);
                    return new UpdateStatus(ResponseStatus.ERROR, message);
                }

                sha1HashResult = HexFormat.of().withLowerCase().formatHex(md.digest());
            }

            if (!sha1Hash.equalsIgnoreCase(sha1HashResult)) {
                final String message = wrongHash(url, sha1Hash, overallread, sha1HashResult);
                return new UpdateStatus(ResponseStatus.ERROR, message);
            }

            final String message = "Downloaded " + url + " (" + overallread + " bytes)";
            LOGGER.debug(message);
            return new UpdateStatus(ResponseStatus.SUCCESSFUL, message);
        }

        private static long getOverallRead(final CloseableHttpResponse response, final MessageDigest md)
                throws IOException {
            long overallread = 0;
            try (final BufferedInputStream bis = new BufferedInputStream(response.getEntity().getContent())) {
                final byte[] buff = new byte[4 *1024];
                for (int read; (read = bis.read(buff)) != -1; overallread += read) {
                    md.update(buff, 0, read);
                }
            }
            return overallread;
        }

        private static String hideTokenDetails(final String targetToken) {
            if (targetToken == null) {
                return "<NULL!>";
            }

            if (targetToken.isEmpty()) {
                return "<EMTPTY!>";
            }

            if (targetToken.length() <= MINIMUM_TOKENLENGTH_FOR_HINT) {
                return "***";
            }

            return targetToken.substring(0, 2) + "***"
                    + targetToken.substring(targetToken.length() - 2, targetToken.length());
        }

        private String wrongHash(final String url, final String sha1Hash, final long overallread,
                final String sha1HashResult) {
            final String message = LOG_PREFIX + DOWNLOAD_LOG_MESSAGE + url + " failed with SHA1 hash missmatch (Expected: "
                    + sha1Hash + BUT_GOT_LOG_MESSAGE + sha1HashResult + ") (" + overallread + " bytes)";
            LOGGER.error(message, device.getTenant(), device.getId());
            return message;
        }

        private String incompleteRead(final String url, final long size, final long overallread) {
            final String message = LOG_PREFIX + DOWNLOAD_LOG_MESSAGE + url + " is incomplete (Expected: " + size
                    + BUT_GOT_LOG_MESSAGE + overallread + ")";
            LOGGER.error(message, device.getTenant(), device.getId());
            return message;
        }

        private String wrongContentLength(final String url, final long size,
                final CloseableHttpResponse response) {
            final String message = LOG_PREFIX + DOWNLOAD_LOG_MESSAGE + url + " has wrong content length (Expected: " + size
                    + BUT_GOT_LOG_MESSAGE + response.getEntity().getContentLength() + ")";
            LOGGER.error(message, device.getTenant(), device.getId());
            return message;
        }

        private String wrongStatusCode(final String url, final CloseableHttpResponse response) {
            final String message = LOG_PREFIX + DOWNLOAD_LOG_MESSAGE + url + " failed (" + response.getStatusLine().getStatusCode()
                    + ")";
            LOGGER.error(message, device.getTenant(), device.getId());
            return message;
        }

        private static CloseableHttpClient createHttpClientThatAcceptsAllServerCerts()
                throws NoSuchAlgorithmException, KeyStoreException, KeyManagementException {
            final SSLContextBuilder builder = SSLContextBuilder.create();
            builder.loadTrustMaterial(null, (chain, authType) -> true);
            final SSLConnectionSocketFactory sslsf = new SSLConnectionSocketFactory(builder.build());
            return HttpClients.custom().setSSLSocketFactory(sslsf).build();
        }
    }

    /**
     * Callback interface which is called when the simulated update process has
     * been finished and the caller of starting the simulated update process can
     * send the result back to the hawkBit update server.
     */
    @FunctionalInterface
    public interface UpdaterCallback {
        /**
         * Callback method to indicate that the simulated update process has
         * been finished.
         *
         * @param device
         *            the device which has been updated
         */
        void sendFeedback(AbstractSimulatedDevice device);
    }

}
