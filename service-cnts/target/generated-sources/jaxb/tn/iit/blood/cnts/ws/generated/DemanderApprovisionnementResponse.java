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
 *         <element name="reference" type="{http://www.w3.org/2001/XMLSchema}string"/>
 *         <element name="quantiteAccordee" type="{http://www.w3.org/2001/XMLSchema}int"/>
 *         <element name="centreSource" type="{http://www.w3.org/2001/XMLSchema}string"/>
 *         <element name="delaiMinutes" type="{http://www.w3.org/2001/XMLSchema}int"/>
 *         <element name="statut" type="{http://www.w3.org/2001/XMLSchema}string"/>
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
    "reference",
    "quantiteAccordee",
    "centreSource",
    "delaiMinutes",
    "statut"
})
@XmlRootElement(name = "demanderApprovisionnementResponse")
public class DemanderApprovisionnementResponse {

    @XmlElement(required = true)
    protected String reference;
    protected int quantiteAccordee;
    @XmlElement(required = true)
    protected String centreSource;
    protected int delaiMinutes;
    @XmlElement(required = true)
    protected String statut;

    /**
     * Obtient la valeur de la propriété reference.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getReference() {
        return reference;
    }

    /**
     * Définit la valeur de la propriété reference.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setReference(String value) {
        this.reference = value;
    }

    /**
     * Obtient la valeur de la propriété quantiteAccordee.
     * 
     */
    public int getQuantiteAccordee() {
        return quantiteAccordee;
    }

    /**
     * Définit la valeur de la propriété quantiteAccordee.
     * 
     */
    public void setQuantiteAccordee(int value) {
        this.quantiteAccordee = value;
    }

    /**
     * Obtient la valeur de la propriété centreSource.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getCentreSource() {
        return centreSource;
    }

    /**
     * Définit la valeur de la propriété centreSource.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setCentreSource(String value) {
        this.centreSource = value;
    }

    /**
     * Obtient la valeur de la propriété delaiMinutes.
     * 
     */
    public int getDelaiMinutes() {
        return delaiMinutes;
    }

    /**
     * Définit la valeur de la propriété delaiMinutes.
     * 
     */
    public void setDelaiMinutes(int value) {
        this.delaiMinutes = value;
    }

    /**
     * Obtient la valeur de la propriété statut.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getStatut() {
        return statut;
    }

    /**
     * Définit la valeur de la propriété statut.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setStatut(String value) {
        this.statut = value;
    }

}
