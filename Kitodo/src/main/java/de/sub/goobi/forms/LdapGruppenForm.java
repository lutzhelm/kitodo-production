/*
 * (c) Kitodo. Key to digital objects e. V. <contact@kitodo.org>
 *
 * This file is part of the Kitodo project.
 *
 * It is licensed under GNU General Public License version 3 or later.
 *
 * For the full copyright and license information, please read the
 * GPL3-License.txt file that was distributed with this source code.
 */

package de.sub.goobi.forms;

import de.sub.goobi.helper.Helper;
import de.sub.goobi.helper.Page;

import java.util.List;
import java.util.Objects;

import javax.annotation.PostConstruct;
import javax.enterprise.context.SessionScoped;
import javax.faces.context.FacesContext;
import javax.inject.Named;

import org.kitodo.data.database.beans.LdapGroup;
import org.kitodo.data.database.exceptions.DAOException;
import org.kitodo.services.ServiceManager;

@Named("LdapGruppenForm")
@SessionScoped
public class LdapGruppenForm extends BasisForm {
    private static final long serialVersionUID = -5644561256582235244L;
    private LdapGroup myLdapGruppe = new LdapGroup();
    private int itemId;
    private transient ServiceManager serviceManager = new ServiceManager();

    /**
     * Create new LDAP group.
     * 
     * @return page
     */
    public String Neu() {
        this.myLdapGruppe = new LdapGroup();
        this.itemId = 0;
        return redirectToEdit("?faces-redirect=true");
    }

    /**
     * Save.
     *
     * @return page or empty String
     */
    public String Speichern() {
        try {
            this.serviceManager.getLdapGroupService().save(this.myLdapGruppe);
            return redirectToList("?faces-redirect=true");
        } catch (DAOException e) {
            Helper.setFehlerMeldung("Could not save", e.getMessage());
            return null;
        }
    }

    /**
     * Remove.
     *
     * @return page or empty String
     */
    public String Loeschen() {
        try {
            this.serviceManager.getLdapGroupService().remove(this.myLdapGruppe);
        } catch (DAOException e) {
            Helper.setFehlerMeldung("Could not delete from database", e.getMessage());
            return null;
        }
        return redirectToList("?faces-redirect=true");
    }

    /**
     * No filter.
     *
     * @return page or empty String
     */
    public String filterKein() {
        List<LdapGroup> ldapGroups = serviceManager.getLdapGroupService().getAll();
        this.page = new Page(0, ldapGroups);
        return "/pages/LdapGruppenAlle";
    }

    /**
     * Method being used as viewAction for ldap group edit form. If 'itemId' is
     * '0', the form for creating a new ldap group will be displayed.
     */
    public void loadLdapGroup() {
        try {
            if (!Objects.equals(this.itemId, 0)) {
                setMyLdapGruppe(this.serviceManager.getLdapGroupService().getById(this.itemId));
            }
        } catch (DAOException e) {
            Helper.setFehlerMeldung("Error retrieving Ldap group with ID '" + this.itemId + "'; ", e.getMessage());
        }
    }

    /**
     * This method initializes the ldap group list without filters.
     */
    @PostConstruct
    public void initializeLdapGroupList() {
        filterKein();
    }

    /*
     * Getter und Setter
     */

    public LdapGroup getMyLdapGruppe() {
        return this.myLdapGruppe;
    }

    public void setMyLdapGruppe(LdapGroup myLdapGruppe) {
        this.myLdapGruppe = myLdapGruppe;
    }

    public void setItemId(int id) {
        this.itemId = id;
    }

    public int getItemId() {
        return this.itemId;
    }

    // TODO:
    // replace calls to this function with "/pages/ldapgroupEdit" once we have
    // completely switched to the new frontend pages
    private String redirectToEdit(String urlSuffix) {
        try {
            String referrer = FacesContext.getCurrentInstance().getExternalContext().getRequestHeaderMap()
                    .get("referer");
            String callerViewId = referrer.substring(referrer.lastIndexOf("/") + 1);
            if (!callerViewId.isEmpty() && callerViewId.contains("users.jsf")) {
                return "/pages/ldapgroupEdit" + urlSuffix;
            } else {
                return "/pages/LdapGruppeBearbeiten" + urlSuffix;
            }
        } catch (NullPointerException e) {
            // This NPE gets thrown - and therefore must be caught - when "LdapGruppenForm" is
            // used from it's integration test
            // class "LdapGruppenFormIT", where no "FacesContext" is available!
            return "/pages/LdapGruppeBearbeiten" + urlSuffix;
        }
    }

    // TODO:
    // replace calls to this function with "/pages/users" once we have completely
    // switched to the new frontend pages
    private String redirectToList(String urlSuffix) {
        try {
            String referrer = FacesContext.getCurrentInstance().getExternalContext().getRequestHeaderMap()
                    .get("referer");
            String callerViewId = referrer.substring(referrer.lastIndexOf("/") + 1);
            if (!callerViewId.isEmpty() && callerViewId.contains("ldapgroupEdit.jsf")) {
                return "/pages/users" + urlSuffix;
            } else {
                return "/pages/LdapGruppenAlle" + urlSuffix;
            }
        } catch (NullPointerException e) {
            // This NPE gets thrown - and therefore must be caught - when "LdapGruppenForm" is
            // used from it's integration test
            // class "LdapGruppenFormIT", where no "FacesContext" is available!
            return "/pages/LdapGruppenAlle" + urlSuffix;
        }
    }
}
