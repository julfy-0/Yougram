package org.telegram.messenger.voip;

public final class Instance {
    public static final int NET_TYPE_UNKNOWN = 0, NET_TYPE_GPRS = 1, NET_TYPE_EDGE = 2, NET_TYPE_3G = 3,
            NET_TYPE_HSPA = 4, NET_TYPE_LTE = 5, NET_TYPE_WIFI = 6, NET_TYPE_ETHERNET = 7,
            NET_TYPE_OTHER_HIGH_SPEED = 8, NET_TYPE_OTHER_LOW_SPEED = 9, NET_TYPE_DIALUP = 10, NET_TYPE_OTHER_MOBILE = 11;
    public static final int ENDPOINT_TYPE_INET = 0, ENDPOINT_TYPE_LAN = 1, ENDPOINT_TYPE_UDP_RELAY = 2, ENDPOINT_TYPE_TCP_RELAY = 3;
    public static final int STATE_WAIT_INIT = 1, STATE_WAIT_INIT_ACK = 2, STATE_ESTABLISHED = 3, STATE_FAILED = 4, STATE_RECONNECTING = 5;
    public static final int DATA_SAVING_NEVER = 0;

    private Instance() {}

    public interface OnStateUpdatedListener { void onStateUpdated(int state); }
    public interface OnSignalBarsUpdatedListener { void onSignalBarsUpdated(int signalBars); }
    public interface OnSignalingDataListener { void onSignalingData(byte[] data); }
    public interface OnRemoteMediaStateUpdatedListener { void onMediaStateUpdated(int audioState, int videoState); }

    // Поля классов ниже читает C++ по именам: переименовывать нельзя.

    public static final class Config {
        public final double initializationTimeout;
        public final double receiveTimeout;
        public final int dataSaving;
        public final boolean enableP2p;
        public final boolean enableAec;
        public final boolean enableNs;
        public final boolean enableAgc;
        public final boolean enableCallUpgrade;
        public final String logPath;
        public final String statsLogPath;
        public final int maxApiLayer;
        public final boolean enableSm;
        public final String customParameters;

        public Config(double initializationTimeout, double receiveTimeout, int dataSaving, boolean enableP2p,
                      boolean enableAec, boolean enableNs, boolean enableAgc, boolean enableCallUpgrade,
                      boolean enableSm, String logPath, String statsLogPath, int maxApiLayer, String customParameters) {
            this.initializationTimeout = initializationTimeout;
            this.receiveTimeout = receiveTimeout;
            this.dataSaving = dataSaving;
            this.enableP2p = enableP2p;
            this.enableAec = enableAec;
            this.enableNs = enableNs;
            this.enableAgc = enableAgc;
            this.enableCallUpgrade = enableCallUpgrade;
            this.logPath = logPath;
            this.statsLogPath = statsLogPath;
            this.maxApiLayer = maxApiLayer;
            this.enableSm = enableSm;
            this.customParameters = customParameters;
        }
    }

    public static final class Endpoint {
        public final boolean isRtc;
        public final long id;
        public final String ipv4;
        public final String ipv6;
        public final int port;
        public final int type;
        public final byte[] peerTag;
        public final boolean turn;
        public final boolean stun;
        public final String username;
        public final String password;
        public final boolean tcp;
        public int reflectorId;

        public Endpoint(boolean isRtc, long id, String ipv4, String ipv6, int port, int type, byte[] peerTag,
                        boolean turn, boolean stun, String username, String password, boolean tcp) {
            this.isRtc = isRtc;
            this.id = id;
            this.ipv4 = ipv4;
            this.ipv6 = ipv6;
            this.port = port;
            this.type = type;
            this.peerTag = peerTag;
            this.turn = turn;
            this.stun = stun;
            if (isRtc) {
                this.username = username;
                this.password = password;
            } else if (peerTag != null) {
                this.username = "reflector";
                StringBuilder sb = new StringBuilder();
                for (byte b : peerTag) sb.append(String.format("%02x", b));
                this.password = sb.toString();
            } else {
                this.username = null;
                this.password = null;
            }
            this.tcp = tcp;
        }
    }

    public static final class Proxy {
        public final String host;
        public final int port;
        public final String login;
        public final String password;

        public Proxy(String host, int port, String login, String password) {
            this.host = host;
            this.port = port;
            this.login = login;
            this.password = password;
        }
    }

    public static final class EncryptionKey {
        public final byte[] value;
        public final boolean isOutgoing;

        public EncryptionKey(byte[] value, boolean isOutgoing) {
            this.value = value;
            this.isOutgoing = isOutgoing;
        }
    }

    public static final class FinalState {
        public final byte[] persistentState;
        public String debugLog;
        public final TrafficStats trafficStats;
        public final boolean isRatingSuggested;

        public FinalState(byte[] persistentState, String debugLog, TrafficStats trafficStats, boolean isRatingSuggested) {
            this.persistentState = persistentState;
            this.debugLog = debugLog;
            this.trafficStats = trafficStats;
            this.isRatingSuggested = isRatingSuggested;
        }
    }

    public static final class TrafficStats {
        public final long bytesSentWifi, bytesReceivedWifi, bytesSentMobile, bytesReceivedMobile;

        public TrafficStats(long sentWifi, long recvWifi, long sentMobile, long recvMobile) {
            this.bytesSentWifi = sentWifi;
            this.bytesReceivedWifi = recvWifi;
            this.bytesSentMobile = sentMobile;
            this.bytesReceivedMobile = recvMobile;
        }
    }

    public static final class Fingerprint {
        public final String hash, setup, fingerprint;

        public Fingerprint(String hash, String setup, String fingerprint) {
            this.hash = hash;
            this.setup = setup;
            this.fingerprint = fingerprint;
        }
    }
}
