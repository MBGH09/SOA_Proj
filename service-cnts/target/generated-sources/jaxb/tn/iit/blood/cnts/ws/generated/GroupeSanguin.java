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
 * <p>Classe Java pour GroupeSanguin.</p>
 * 
 * <p>Le fragment de schéma suivant indique le contenu attendu figurant dans cette classe.</p>
 * <pre>{@code
 * <simpleType name="GroupeSanguin">
 *   <restriction base="{http://www.w3.org/2001/XMLSchema}string">
 *     <enumeration value="A_POS"/>
 *     <enumeration value="A_NEG"/>
 *     <enumeration value="B_POS"/>
 *     <enumeration value="B_NEG"/>
 *     <enumeration value="AB_POS"/>
 *     <enumeration value="AB_NEG"/>
 *     <enumeration value="O_POS"/>
 *     <enumeration value="O_NEG"/>
 *   </restriction>
 * </simpleType>
 * }</pre>
 * 
 */
@XmlType(name = "GroupeSanguin")
@XmlEnum
public enum GroupeSanguin {

    A_POS,
    A_NEG,
    B_POS,
    B_NEG,
    AB_POS,
    AB_NEG,
    O_POS,
    O_NEG;

    public String value() {
        return name();
    }

    public static GroupeSanguin fromValue(String v) {
        return valueOf(v);
    }

}
