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
 * <p>Classe Java pour Composant.</p>
 * 
 * <p>Le fragment de schéma suivant indique le contenu attendu figurant dans cette classe.</p>
 * <pre>{@code
 * <simpleType name="Composant">
 *   <restriction base="{http://www.w3.org/2001/XMLSchema}string">
 *     <enumeration value="CGR"/>
 *     <enumeration value="PLASMA"/>
 *     <enumeration value="PLAQUETTES"/>
 *   </restriction>
 * </simpleType>
 * }</pre>
 * 
 */
@XmlType(name = "Composant")
@XmlEnum
public enum Composant {

    CGR,
    PLASMA,
    PLAQUETTES;

    public String value() {
        return name();
    }

    public static Composant fromValue(String v) {
        return valueOf(v);
    }

}
