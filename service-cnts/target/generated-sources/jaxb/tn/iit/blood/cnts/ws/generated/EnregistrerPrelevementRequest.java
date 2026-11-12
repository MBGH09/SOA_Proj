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
 *         <element name="groupeSanguin" type="{http://iit.tn/soa/cnts}GroupeSanguin"/>
 *         <element name="volumeMl" type="{http://www.w3.org/2001/XMLSchema}int"/>
 *         <element name="centreId" type="{http://www.w3.org/2001/XMLSchema}string"/>
 *         <element name="datePrelevement" type="{http://www.w3.org/2001/XMLSchema}date"/>
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
    "groupeSanguin",
    "volumeMl",
    "centreId",
    "datePrelevement"
})
@XmlRootElement(name = "enregistrerPrelevementRequest")
public class EnregistrerPrelevementRequest {

    @XmlElement(required = true)
    protected String donIdLocal;
    @XmlElement(required = true)
    @XmlSchemaType(name = "string")
    protected GroupeSanguin groupeSanguin;
    protected int volumeMl;
    @XmlElement(required = true)
    protected String centreId;
    @XmlElement(required = true)
    @XmlSchemaType(name = "date")
    protected XMLGregorianCalendar datePrelevement;

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
     * Obtient la valeur de la propriété volumeMl.
     * 
     */
    public int getVolumeMl() {
        return volumeMl;
    }

    /**
     * Définit la valeur de la propriété volumeMl.
     * 
     */
    public void setVolumeMl(int value) {
        this.volumeMl = value;
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
     * Obtient la valeur de la propriété datePrelevement.
     * 
     * @return
     *     possible object is
     *     {@link XMLGregorianCalendar }
     *     
     */
    public XMLGregorianCalendar getDatePrelevement() {
        return datePrelevement;
    }

    /**
     * Définit la valeur de la propriété datePrelevement.
     * 
     * @param value
     *     allowed object is
     *     {@link XMLGregorianCalendar }
     *     
     */
    public void setDatePrelevement(XMLGregorianCalendar value) {
        this.datePrelevement = value;
    }

}
