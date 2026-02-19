package com.reigninblood.TaleMon_PokeBall.util;

import org.bson.BsonDocument;
import org.bson.BsonValue;

public final class MetaReadUtil {

    private MetaReadUtil() {}

    public static Captured readCapturedEntity(BsonDocument meta) {
        if (meta == null) return null;

        BsonValue ce = meta.get("CapturedEntity");
        if (ce == null || !ce.isDocument()) return null;

        BsonDocument d = ce.asDocument();

        Integer roleIndex = null;
        String npcNameKey = null;

        BsonValue ri = d.get("RoleIndex");
        if (ri != null && ri.isInt32()) roleIndex = ri.asInt32().getValue();

        BsonValue nk = d.get("NpcNameKey");
        if (nk != null && nk.isString()) npcNameKey = nk.asString().getValue();

        if (npcNameKey == null) {
            BsonValue rn = d.get("RoleName");
            if (rn != null && rn.isString()) npcNameKey = rn.asString().getValue();
        }
        if (npcNameKey == null) {
            BsonValue rid = d.get("RoleIdentifier");
            if (rid != null && rid.isString()) npcNameKey = rid.asString().getValue();
        }

        if (roleIndex == null && npcNameKey == null) return null;
        return new Captured(roleIndex, npcNameKey);
    }

    public record Captured(Integer roleIndex, String npcNameKey) {}
}
