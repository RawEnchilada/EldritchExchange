package dev.eldritch.exchange.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class LedgerData {

    public record Burner(int x, int y, int z) {
    }

    public static final class Account {
        private long essence;
        private int madness;

        // Madness pacing.
        private long lastGainTick;
        private long lastFedTick;
        private long lastWarningTick;
        private int starvationTier;

        // Thal'ggor demand.
        private String demandItemId;
        private int demandCount;
        private long demandIssuedTick;
        private long demandDeadlineTick;
        private String demandState = "NONE";
        private Demand.Kind demandKind;
        private final List<String> sealed = new ArrayList<>();
        private int fulfilledDemands;
        private boolean nameRevealed;

        // Favour: overall progression. Fulfilling demands raises it, letting them expire lowers it.
        private int favour;
        // Items dropped from this player's Codex after a demand expired.
        private final List<String> forgotten = new ArrayList<>();

        // Tithe of the Abyss debt.
        private String titheItemId;
        private int titheCount;
        private long titheTakenTick;
        private long titheReclaimDeadline;
        private boolean titheExpired;

        public long essence() {
            return essence;
        }

        public int madness() {
            return madness;
        }

        public long lastGainTick() {
            return lastGainTick;
        }

        public void setLastGainTick(long value) {
            lastGainTick = value;
        }

        public long lastFedTick() {
            return lastFedTick;
        }

        public void setLastFedTick(long value) {
            lastFedTick = value;
        }

        public long lastWarningTick() {
            return lastWarningTick;
        }

        public void setLastWarningTick(long value) {
            lastWarningTick = value;
        }

        public int starvationTier() {
            return starvationTier;
        }

        public void setStarvationTier(int value) {
            starvationTier = value;
        }

        public String demandItemId() {
            return demandItemId;
        }

        public int demandCount() {
            return demandCount;
        }

        public long demandIssuedTick() {
            return demandIssuedTick;
        }

        public long demandDeadlineTick() {
            return demandDeadlineTick;
        }

        public String demandState() {
            return demandState;
        }

        public List<String> sealed() {
            return sealed;
        }

        public int fulfilledDemands() {
            return fulfilledDemands;
        }

        public void setFulfilledDemands(int value) {
            fulfilledDemands = value;
        }

        public boolean nameRevealed() {
            return nameRevealed;
        }

        public void setNameRevealed(boolean value) {
            nameRevealed = value;
        }

        public String titheItemId() {
            return titheItemId;
        }

        public int titheCount() {
            return titheCount;
        }

        public long titheTakenTick() {
            return titheTakenTick;
        }

        public long titheReclaimDeadline() {
            return titheReclaimDeadline;
        }

        public boolean titheExpired() {
            return titheExpired;
        }

        void addEssence(long amount) {
            essence += amount;
        }

        boolean spendEssence(long cost) {
            if (essence < cost) {
                return false;
            }
            essence -= cost;
            return true;
        }

        void addMadness(int amount) {
            madness = Madness.clamp(madness + amount);
        }

        void setMadness(int value) {
            madness = Madness.clamp(value);
        }

        public void issueDemand(String itemId, int count, long issuedTick, long deadlineTick, List<String> sealedItems) {
            issueDemand(Demand.Kind.ITEM, itemId, count, issuedTick, deadlineTick, sealedItems);
        }

        public void issueDemand(Demand.Kind kind, String itemId, int count, long issuedTick, long deadlineTick,
                                List<String> sealedItems) {
            demandKind = kind;
            demandItemId = itemId;
            demandCount = count;
            demandIssuedTick = issuedTick;
            demandDeadlineTick = deadlineTick;
            demandState = "ACTIVE";
            sealed.clear();
            sealed.addAll(sealedItems);
        }

        public Demand.Kind demandKind() {
            return demandKind;
        }

        public void setDemandKind(Demand.Kind kind) {
            demandKind = kind;
        }

        public int favour() {
            return favour;
        }

        public void addFavour(int delta) {
            favour = Math.max(-10, Math.min(10, favour + delta));
            if (favour >= Demand.NAME_FAVOUR) {
                nameRevealed = true;
            }
        }

        public void setFavour(int value) {
            favour = Math.max(-10, Math.min(10, value));
            if (favour >= Demand.NAME_FAVOUR) {
                nameRevealed = true;
            }
        }

        public List<String> forgotten() {
            return forgotten;
        }

        public boolean isForgotten(String itemId) {
            return forgotten.contains(itemId);
        }

        public void forget(List<String> itemIds) {
            for (String id : itemIds) {
                if (!forgotten.contains(id)) {
                    forgotten.add(id);
                }
            }
        }

        public void clearDemand() {
            demandKind = null;
            demandItemId = null;
            demandCount = 0;
            demandIssuedTick = 0;
            demandDeadlineTick = 0;
            demandState = "NONE";
            sealed.clear();
        }

        public void fulfilDemand() {
            demandState = "FULFILLED";
        }

        public void expireDemand() {
            demandState = "EXPIRED";
        }

        public void setDemandState(String state) {
            demandState = state == null || state.isEmpty() ? "NONE" : state;
        }

        public boolean isSealed(String itemId) {
            return sealed.contains(itemId);
        }

        public void issueTithe(String itemId, int count, long takenTick, long deadline) {
            titheItemId = itemId;
            titheCount = count;
            titheTakenTick = takenTick;
            titheReclaimDeadline = deadline;
            titheExpired = false;
        }

        public void clearTithe() {
            titheItemId = null;
            titheCount = 0;
            titheTakenTick = 0;
            titheReclaimDeadline = 0;
            titheExpired = false;
        }

        public void expireTithe() {
            titheExpired = true;
        }
    }

    private final Map<UUID, Account> accounts = new LinkedHashMap<>();
    private final Map<UUID, Burner> burners = new LinkedHashMap<>();
    private final List<String> learned = new ArrayList<>();

    public Account account(UUID player) {
        return accounts.computeIfAbsent(player, k -> new Account());
    }

    public Map<UUID, Account> accounts() {
        return Collections.unmodifiableMap(accounts);
    }

    public void addEssence(UUID player, long amount) {
        account(player).addEssence(amount);
    }

    public boolean spendEssence(UUID player, long cost) {
        return account(player).spendEssence(cost);
    }

    public void addMadness(UUID player, int amount) {
        account(player).addMadness(amount);
    }

    public void setMadness(UUID player, int value) {
        account(player).setMadness(value);
    }

    public boolean learn(String itemId) {
        if (learned.contains(itemId)) {
            return false;
        }
        learned.add(itemId);
        return true;
    }

    public List<String> learned() {
        return Collections.unmodifiableList(learned);
    }

    public Map<UUID, Burner> burners() {
        return Collections.unmodifiableMap(burners);
    }

    public Burner burner(UUID player) {
        return burners.get(player);
    }

    public void bindBurner(UUID player, Burner burner) {
        burners.put(player, burner);
        account(player).setLastFedTick(0); // set by service to current tick
    }

    public void unbindBurner(UUID player) {
        burners.remove(player);
    }
}