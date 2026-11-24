package tn.iit.blood.service_don_stock.model;

import java.io.Serializable;
import java.util.Objects;

public class StockNiveauId implements Serializable {

    private String lieuId;
    private String groupeSanguin;
    private String composant;

    public StockNiveauId() {}

    public StockNiveauId(String lieuId, String groupeSanguin, String composant) {
        this.lieuId = lieuId;
        this.groupeSanguin = groupeSanguin;
        this.composant = composant;
    }

    public String getLieuId()         { return lieuId; }
    public String getGroupeSanguin()  { return groupeSanguin; }
    public String getComposant()      { return composant; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof StockNiveauId that)) return false;
        return Objects.equals(lieuId, that.lieuId)
            && Objects.equals(groupeSanguin, that.groupeSanguin)
            && Objects.equals(composant, that.composant);
    }

    @Override
    public int hashCode() {
        return Objects.hash(lieuId, groupeSanguin, composant);
    }
}
