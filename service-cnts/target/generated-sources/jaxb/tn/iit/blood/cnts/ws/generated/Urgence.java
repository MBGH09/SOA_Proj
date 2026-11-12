//
// Ce fichier a été généré par Eclipse Implementation of JAXB, v4.0.5 
// Voir https://eclipse-ee4j.github.io/jaxb-ri 
// Toute modification apportée à ce fichier sera perdue lors de la recompilation du schéma source. 
//


package tn.iit.blood.cnts.ws.generated;

import jakarta.xml.bind.annotation.XmlEnum;
import jakarta.xml.bind.annotation.XmlType;


/**
 * 
 * 
 * <p>Classe Java pour Urgence.</p>
 * 
 * <p>Le fragment de schéma suivant indique le contenu attendu figurant dans cette classe.</p>
 * <pre>{@code
 * <simpleType name="Urgence">
 *   <restriction base="{http://www.w3.org/2001/XMLSchema}string">
 *     <enumeration value="NORMALE"/>
 *     <enumeration value="HAUTE"/>
 *     <enumeration value="CRITIQUE"/>
 *   </restriction>
 * </simpleType>
 * }</pre>
 * 
 */
@XmlType(name = "Urgence")
@XmlEnum
public enum Urgence {

    NORMALE,
    HAUTE,
    CRITIQUE;

    public String value() {
        return name();
    }

    public static Urgence fromValue(String v) {
        return valueOf(v);
    }

}
