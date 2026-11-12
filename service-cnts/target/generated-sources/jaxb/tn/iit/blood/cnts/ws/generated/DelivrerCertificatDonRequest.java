//
// Ce fichier a été généré par Eclipse Implementation of JAXB, v4.0.5 
// Voir https://eclipse-ee4j.github.io/jaxb-ri 
// Toute modification apportée à ce fichier sera perdue lors de la recompilation du schéma source. 
//


package tn.iit.blood.cnts.ws.generated;

import javax.xml.datatype.XMLGregorianCalendar;
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
 *         <element name="donIdLocal" type="{http://www.w3.org/2001/XMLSchema}string"/>
 *         <element name="numeroDonNational" type="{http://www.w3.org/2001/XMLSchema}string"/>
 *         <element name="donneurRef" type="{http://www.w3.org/2001/XMLSchema}string"/>
 *         <element name="centreId" type="{http://www.w3.org/2001/XMLSchema}string"/>
 *         <element name="dateDon" type="{http://www.w3.org/2001/XMLSchema}date"/>
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
    "donIdLocal",
    "numeroDonNational",
    "donneurRef",
    "centreId",
    "dateDon"
})
@XmlRootElement(name = "delivrerCertificatDonRequest")
public class DelivrerCertificatDonRequest {

    @XmlElement(required = true)
    protected String donIdLocal;
    @XmlElement(required = true)
    protected String numeroDonNational;
    @XmlElement(required = true)
    protected String donneurRef;
    @XmlElement(required = true)
    protected String centreId;
    @XmlElement(required = true)
    @XmlSchemaType(name = "date")
    protected XMLGregorianCalendar dateDon;

    /**
     * Obtient la valeur de la propriété donIdLocal.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getDonIdLocal() {
        return donIdLocal;
    }

    /**
     * Définit la valeur de la propriété donIdLocal.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setDonIdLocal(String value) {
        this.donIdLocal = value;
    }

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
     * Obtient la valeur de la propriété donneurRef.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getDonneurRef() {
        return donneurRef;
    }

    /**
     * Définit la valeur de la propriété donneurRef.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setDonneurRef(String value) {
        this.donneurRef = value;
    }

    /**
     * Obtient la valeur de la propriété centreId.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getCentreId() {
        return centreId;
    }

    /**
     * Définit la valeur de la propriété centreId.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setCentreId(String value) {
        this.centreId = value;
    }

    /**
     * Obtient la valeur de la propriété dateDon.
     * 
     * @return
     *     possible object is
     *     {@link XMLGregorianCalendar }
     *     
     */
    public XMLGregorianCalendar getDateDon() {
        return dateDon;
    }

    /**
     * Définit la valeur de la propriété dateDon.
     * 
     * @param value
     *     allowed object is
     *     {@link XMLGregorianCalendar }
     *     
     */
    public void setDateDon(XMLGregorianCalendar value) {
        this.dateDon = value;
    }

}
