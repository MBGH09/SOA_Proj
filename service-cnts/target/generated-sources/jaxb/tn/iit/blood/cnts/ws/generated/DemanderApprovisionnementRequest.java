//
// Ce fichier a été généré par Eclipse Implementation of JAXB, v4.0.5 
// Voir https://eclipse-ee4j.github.io/jaxb-ri 
// Toute modification apportée à ce fichier sera perdue lors de la recompilation du schéma source. 
//


package tn.iit.blood.cnts.ws.generated;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;
import jakarta.xml.bind.annotation.XmlSchemaType;
import jakarta.xml.bind.annotation.XmlType;


/**
 * <p>Classe Java pour anonymous complex type.</p>
 * 
 * <p>Le fragment de schéma suivant indique le contenu attendu figurant dans cette classe.</p>
 * 
 * <pre>{@code
 * <complexType>
 *   <complexContent>
 *     <restriction base="{http://www.w3.org/2001/XMLSchema}anyType">
 *       <sequence>
 *         <element name="hopitalId" type="{http://www.w3.org/2001/XMLSchema}string"/>
 *         <element name="region" type="{http://www.w3.org/2001/XMLSchema}string"/>
 *         <element name="groupeSanguin" type="{http://iit.tn/soa/cnts}GroupeSanguin"/>
 *         <element name="composant" type="{http://iit.tn/soa/cnts}Composant"/>
 *         <element name="quantite" type="{http://www.w3.org/2001/XMLSchema}int"/>
 *         <element name="urgence" type="{http://iit.tn/soa/cnts}Urgence"/>
 *         <element name="demandeId" type="{http://www.w3.org/2001/XMLSchema}string"/>
 *       </sequence>
 *     </restriction>
 *   </complexContent>
 * </complexType>
 * }</pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "", propOrder = {
    "hopitalId",
    "region",
    "groupeSanguin",
    "composant",
    "quantite",
    "urgence",
    "demandeId"
})
@XmlRootElement(name = "demanderApprovisionnementRequest")
public class DemanderApprovisionnementRequest {

    @XmlElement(required = true)
    protected String hopitalId;
    @XmlElement(required = true)
    protected String region;
    @XmlElement(required = true)
    @XmlSchemaType(name = "string")
    protected GroupeSanguin groupeSanguin;
    @XmlElement(required = true)
    @XmlSchemaType(name = "string")
    protected Composant composant;
    protected int quantite;
    @XmlElement(required = true)
    @XmlSchemaType(name = "string")
    protected Urgence urgence;
    @XmlElement(required = true)
    protected String demandeId;

    /**
     * Obtient la valeur de la propriété hopitalId.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getHopitalId() {
        return hopitalId;
    }

    /**
     * Définit la valeur de la propriété hopitalId.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setHopitalId(String value) {
        this.hopitalId = value;
    }

    /**
     * Obtient la valeur de la propriété region.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getRegion() {
        return region;
    }

    /**
     * Définit la valeur de la propriété region.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setRegion(String value) {
        this.region = value;
    }

    /**
     * Obtient la valeur de la propriété groupeSanguin.
     * 
     * @return
     *     possible object is
     *     {@link GroupeSanguin }
     *     
     */
    public GroupeSanguin getGroupeSanguin() {
        return groupeSanguin;
    }

    /**
     * Définit la valeur de la propriété groupeSanguin.
     * 
     * @param value
     *     allowed object is
     *     {@link GroupeSanguin }
     *     
     */
    public void setGroupeSanguin(GroupeSanguin value) {
        this.groupeSanguin = value;
    }

    /**
     * Obtient la valeur de la propriété composant.
     * 
     * @return
     *     possible object is
     *     {@link Composant }
     *     
     */
    public Composant getComposant() {
        return composant;
    }

    /**
     * Définit la valeur de la propriété composant.
     * 
     * @param value
     *     allowed object is
     *     {@link Composant }
     *     
     */
    public void setComposant(Composant value) {
        this.composant = value;
    }

    /**
     * Obtient la valeur de la propriété quantite.
     * 
     */
    public int getQuantite() {
        return quantite;
    }

    /**
     * Définit la valeur de la propriété quantite.
     * 
     */
    public void setQuantite(int value) {
        this.quantite = value;
    }

    /**
     * Obtient la valeur de la propriété urgence.
     * 
     * @return
     *     possible object is
     *     {@link Urgence }
     *     
     */
    public Urgence getUrgence() {
        return urgence;
    }

    /**
     * Définit la valeur de la propriété urgence.
     * 
     * @param value
     *     allowed object is
     *     {@link Urgence }
     *     
     */
    public void setUrgence(Urgence value) {
        this.urgence = value;
    }

    /**
     * Obtient la valeur de la propriété demandeId.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getDemandeId() {
        return demandeId;
    }

    /**
     * Définit la valeur de la propriété demandeId.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setDemandeId(String value) {
        this.demandeId = value;
    }

}
