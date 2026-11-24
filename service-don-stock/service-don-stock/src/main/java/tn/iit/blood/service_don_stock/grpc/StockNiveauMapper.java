package tn.iit.blood.service_don_stock.grpc;

import tn.iit.blood.donstock.v1.*;
import tn.iit.blood.service_don_stock.model.Lieu;
import tn.iit.blood.service_don_stock.model.StockNiveau;

import java.time.Instant;

/**
 * Maps a StockNiveau JPA entity to the protobuf NiveauStockMessage.
 * The stream carries the study contract (quantite, seuil_alerte, niveau,
 * timestamp); the niveau is derived from the available quantity and the
 * configured alert threshold.
 */
public final class StockNiveauMapper {

    private StockNiveauMapper() {}

    public static NiveauStockMessage toProto(StockNiveau s, Lieu lieu) {
        return NiveauStockMessage.newBuilder()
                .setLieuId(s.getLieuId())
                .setLieuNom(lieu != null ? lieu.getNom() : s.getLieuId())
                .setGroupeSanguin(parseGroupe(s.getGroupeSanguin()))
                .setComposant(parseComposant(s.getComposant()))
                // Legacy counters are kept for existing subscribers (fields 5-7).
                .setDisponible(s.getDisponible())
                .setReserve(s.getReserve())
                .setEnTransit(s.getEnTransit())
                .setNiveau(computeNiveau(s))
                .setQuantite(s.getDisponible())
                .setSeuilAlerte(s.getSeuilAlerte())
                .setTimestamp(Instant.now().toString())
                .build();
    }

    public static Niveau computeNiveau(StockNiveau s) {
        int d = s.getDisponible();
        int seuil = s.getSeuilAlerte();
        if (seuil <= 0) return Niveau.HAUT;
        if (d <= seuil)        return Niveau.BAS;
        if (d <= 2 * seuil)    return Niveau.MOYEN;
        return Niveau.HAUT;
    }

    public static GroupeSanguin parseGroupe(String name) {
        try { return GroupeSanguin.valueOf(name); }
        catch (IllegalArgumentException e) { return GroupeSanguin.GROUPE_SANGUIN_UNSPECIFIED; }
    }

    public static Composant parseComposant(String name) {
        try { return Composant.valueOf(name); }
        catch (IllegalArgumentException e) { return Composant.COMPOSANT_UNSPECIFIED; }
    }
}
