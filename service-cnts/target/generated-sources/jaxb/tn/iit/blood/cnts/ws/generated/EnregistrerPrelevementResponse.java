//
// Ce fichier a été généré par Eclipse Implementation of JAXB, v4.0.5 
// Voir https://eclipse-ee4j.github.io/jaxb-ri 
// Toute modification apportée à ce fichier sera perdue lors de la recompilation du schéma source. 
//


package tn.iit.blood.cnts.ws.generated;

import java.util.ArrayList;
import java.util.List;
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
 *         <element name="numeroDonNational" type="{http://www.w3.org/2001/XMLSchema}string"/>
 *         <element name="statut" type="{http://www.w3.org/2001/XMLSchema}string"/>
 *         <element name="composantsPrevus" type="{http://www.w3.org/2001/XMLSchema}string" maxOccurs="3"/>
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
    "numeroDonNational",
    "statut",
    "composantsPrevus"
})
@XmlRootElement(name = "enregistrerPrelevementResponse")
public class EnregistrerPrelevementResponse {

    @XmlElement(required = true)
    protected String numeroDonNational;
    @XmlElement(required = true)
    protected String statut;
    @XmlElement(required = true)
    protected List<String> composantsPrevus;

    /**
     * Obtient la valeur de la propriété numeroDonNational.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getNumeroDonNational() {
        return numeroDonNational;
    }

    /**
     * Définit la valeur de la propriété numeroDonNational.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setNumeroDonNational(String value) {
        this.numeroDonNational = value;
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

    /**
     * Gets the value of the composantsPrevus property.
     * 
     * <p>This accessor method returns a reference to the live list,
     * not a snapshot. Therefore any modification you make to the
     * returned list will be present inside the JAXB object.
     * This is why there is not a <CODE>set</CODE> method for the composantsPrevus property.</p>
     * 
     * <p>
     * For example, to add a new item, do as follows:
     * </p>
     * <pre>
     * getComposantsPrevus().add(newItem);
     * </pre>
     * 
     * 
     * <p>
     * Objects of the following type(s) are allowed in the list
     * {@link String }
     * </p>
     * 
     * 
     * @return
     *     The value of the composantsPrevus property.
     */
    public List<String> getComposantsPrevus() {
        if (composantsPrevus == null) {
            composantsPrevus = new ArrayList<>();
        }
        return this.composantsPrevus;
    }

}
