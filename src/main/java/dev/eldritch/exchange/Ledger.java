package dev.eldritch.exchange;

import dev.eldritch.exchange.core.LedgerData;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

public final class Ledger extends SavedData {

    private static final String NAME = "eldritch_ledger";

    private static final SavedData.Factory<Ledger> FACTORY = new SavedData.Factory<>(Ledger::new, Ledger::load);

    public final LedgerData data;

    public Ledger() {
        this(new LedgerData());
    }

    private Ledger(LedgerData data) {
        this.data = data;
    }

    public static Ledger get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, NAME);
    }

    public static Ledger load(CompoundTag tag, HolderLookup.Provider lookup) {
        LedgerData data = new LedgerData();

        ListTag learned = tag.getList("learned", Tag.TAG_STRING);
        for (int i = 0; i < learned.size(); i++) {
            data.learn(learned.getString(i));
        }

        ListTag accounts = tag.getList("accounts", Tag.TAG_COMPOUND);
        for (int i = 0; i < accounts.size(); i++) {
            CompoundTag entry = accounts.getCompound(i);
            UUID player = UUID.fromString(entry.getString("uuid"));
            data.addEssence(player, entry.getLong("essence"));
            data.setMadness(player, entry.getInt("madness"));

            LedgerData.Account account = data.account(player);
            if (entry.contains("lastGainTick")) {
                account.setLastGainTick(entry.getLong("lastGainTick"));
            }
            if (entry.contains("lastFedTick")) {
                account.setLastFedTick(entry.getLong("lastFedTick"));
            }
            if (entry.contains("lastWarningTick")) {
                account.setLastWarningTick(entry.getLong("lastWarningTick"));
            }
            if (entry.contains("starvationTier")) {
                account.setStarvationTier(entry.getInt("starvationTier"));
            }
            if (entry.contains("fulfilledDemands")) {
                account.setFulfilledDemands(entry.getInt("fulfilledDemands"));
            }
            if (entry.contains("nameRevealed")) {
                account.setNameRevealed(entry.getBoolean("nameRevealed"));
            }

            // Demand.
            if (entry.contains("demandItem")) {
                String demandItem = entry.getString("demandItem");
                if (!demandItem.isEmpty()) {
                    int count = entry.getInt("demandCount");
                    long issued = entry.getLong("demandIssued");
                    long deadline = entry.getLong("demandDeadline");
                    String state = entry.getString("demandState");
                    if (state.isEmpty()) {
                        state = "ACTIVE";
                    }
                    List<String> sealed = new ArrayList<>();
                    if (entry.contains("sealed")) {
                        ListTag sealedTag = entry.getList("sealed", Tag.TAG_STRING);
                        for (int s = 0; s < sealedTag.size(); s++) {
                            sealed.add(sealedTag.getString(s));
                        }
                    }
                    // Only restore if item still learned? Keep as-is; invalid ids cleaned later.
                    account.issueDemand(demandItem, count, issued, deadline, sealed);
                    account.setDemandState(state);
                }
            }

            // Tithe debt.
            if (entry.contains("titheItem")) {
                String titheItem = entry.getString("titheItem");
                if (!titheItem.isEmpty()) {
                    int tcount = entry.getInt("titheCount");
                    long taken = entry.getLong("titheTaken");
                    long reclaim = entry.getLong("titheReclaim");
                    boolean expired = entry.getBoolean("titheExpired");
                    account.issueTithe(titheItem, tcount, taken, reclaim);
                    if (expired) {
                        account.expireTithe();
                    }
                }
            }
        }

        ListTag burners = tag.getList("burners", Tag.TAG_COMPOUND);
        for (int i = 0; i < burners.size(); i++) {
            CompoundTag entry = burners.getCompound(i);
            UUID player = UUID.fromString(entry.getString("uuid"));
            data.bindBurner(player, new LedgerData.Burner(entry.getInt("x"), entry.getInt("y"), entry.getInt("z")));
        }
        return new Ledger(data);
    }

    // Helper to set demand state string without exposing field.
    private static String stateViaReflection(LedgerData.Account account, String state) {
        // Account has no public setter for demandState; use clear/issue again is costly.
        // Instead, we rely on issueDemand setting ACTIVE; adjust by re-issuing not needed.
        // For simplicity, state is tracked via demandState field accessible only here same package? It's package-private class.
        return state;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider lookup) {
        ListTag learned = new ListTag();
        for (String id : data.learned()) {
            learned.add(StringTag.valueOf(id));
        }
        tag.put("learned", learned);

        ListTag accounts = new ListTag();
        for (Map.Entry<UUID, LedgerData.Account> entry : data.accounts().entrySet()) {
            CompoundTag account = new CompoundTag();
            account.putString("uuid", entry.getKey().toString());
            account.putLong("essence", entry.getValue().essence());
            account.putInt("madness", entry.getValue().madness());
            account.putLong("lastGainTick", entry.getValue().lastGainTick());
            account.putLong("lastFedTick", entry.getValue().lastFedTick());
            account.putLong("lastWarningTick", entry.getValue().lastWarningTick());
            account.putInt("starvationTier", entry.getValue().starvationTier());
            account.putInt("fulfilledDemands", entry.getValue().fulfilledDemands());
            account.putBoolean("nameRevealed", entry.getValue().nameRevealed());

            if (entry.getValue().demandItemId() != null) {
                account.putString("demandItem", entry.getValue().demandItemId());
                account.putInt("demandCount", entry.getValue().demandCount());
                account.putLong("demandIssued", entry.getValue().demandIssuedTick());
                account.putLong("demandDeadline", entry.getValue().demandDeadlineTick());
                account.putString("demandState", entry.getValue().demandState());
                ListTag sealed = new ListTag();
                for (String id : entry.getValue().sealed()) {
                    sealed.add(StringTag.valueOf(id));
                }
                account.put("sealed", sealed);
            }

            if (entry.getValue().titheItemId() != null) {
                account.putString("titheItem", entry.getValue().titheItemId());
                account.putInt("titheCount", entry.getValue().titheCount());
                account.putLong("titheTaken", entry.getValue().titheTakenTick());
                account.putLong("titheReclaim", entry.getValue().titheReclaimDeadline());
                account.putBoolean("titheExpired", entry.getValue().titheExpired());
            }

            accounts.add(account);
        }
        tag.put("accounts", accounts);

        ListTag burners = new ListTag();
        for (Map.Entry<UUID, LedgerData.Burner> entry : data.burners().entrySet()) {
            CompoundTag burner = new CompoundTag();
            burner.putString("uuid", entry.getKey().toString());
            burner.putInt("x", entry.getValue().x());
            burner.putInt("y", entry.getValue().y());
            burner.putInt("z", entry.getValue().z());
            burners.add(burner);
        }
        tag.put("burners", burners);
        return tag;
    }
}