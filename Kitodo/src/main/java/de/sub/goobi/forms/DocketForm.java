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

import de.sub.goobi.config.ConfigCore;
import de.sub.goobi.helper.Helper;

import java.io.File;
import java.util.Objects;

import javax.enterprise.context.SessionScoped;
import javax.faces.context.FacesContext;
import javax.inject.Named;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.kitodo.data.database.beans.Docket;
import org.kitodo.data.database.exceptions.DAOException;
import org.kitodo.data.exceptions.DataException;
import org.kitodo.model.LazyDTOModel;
import org.kitodo.services.ServiceManager;
import org.kitodo.services.data.ProcessService;

@Named("DocketForm")
@SessionScoped
public class DocketForm extends BasisForm {
    private static final long serialVersionUID = -445707928042517243L;
    private Docket myDocket = new Docket();
    private transient ServiceManager serviceManager = new ServiceManager();
    private static final Logger logger = LogManager.getLogger(DocketForm.class);
    private int docketId;

    /**
     * Empty default constructor that also sets the LazyDTOModel instance of this
     * bean.
     */
    public DocketForm() {
        super();
        super.setLazyDTOModel(new LazyDTOModel(serviceManager.getDocketService()));
    }

    /**
     * Creates a new Docket.
     *
     * @return the navigation String
     */
    public String newDocket() {
        this.myDocket = new Docket();
        this.docketId = 0;
        return redirectToEdit("?faces-redirect=true");
    }

    /**
     * Save docket.
     *
     * @return page or empty String
     */
    public String save() {
        try {
            if (hasValidRulesetFilePath(myDocket, ConfigCore.getParameter("xsltFolder"))) {
                this.serviceManager.getDocketService().save(myDocket);
                return redirectToList("?faces-redirect=true");
            } else {
                Helper.setFehlerMeldung("DocketNotFound");
                return null;
            }
        } catch (DataException e) {
            Helper.setFehlerMeldung("fehlerNichtSpeicherbar", e.getMessage());
            logger.error(e);
            return null;
        }
    }

    private boolean hasValidRulesetFilePath(Docket d, String pathToRulesets) {
        File rulesetFile = new File(pathToRulesets + d.getFile());
        return rulesetFile.exists();
    }

    /**
     * Delete docket.
     *
     * @return page or empty String
     */
    public String deleteDocket() {
        try {
            if (hasAssignedProcesses(myDocket)) {
                Helper.setFehlerMeldung("DocketInUse");
                return null;
            } else {
                this.serviceManager.getDocketService().remove(this.myDocket);
            }
        } catch (DataException e) {
            Helper.setFehlerMeldung("fehlerNichtLoeschbar", e.getMessage());
            return null;
        }
        return "/pages/DocketList";
    }

    private boolean hasAssignedProcesses(Docket d) throws DataException {
        ProcessService processService = serviceManager.getProcessService();
        Integer number = processService.findByDocket(d).size();
        return number > 0;
    }

    /**
     * Method being used as viewAction for docket edit form. If 'docketId' is
     * '0', the form for creating a new docket will be displayed.
     */
    public void loadDocket() {
        try {
            if (!Objects.equals(this.docketId, 0)) {
                setMyDocket(this.serviceManager.getDocketService().getById(this.docketId));
            }
        } catch (DAOException e) {
            Helper.setFehlerMeldung("Error retrieving docket with ID '" + this.docketId + "'; ", e.getMessage());
        }
    }

    /*
     * Getter und Setter
     */

    public Docket getMyDocket() {
        return this.myDocket;
    }

    public void setMyDocket(Docket docket) {
        Helper.getHibernateSession().clear();
        this.myDocket = docket;
    }

    public int getDocketId() {
        return this.docketId;
    }

    public void setDocketId(int id) {
        this.docketId = id;
    }

    // replace calls to this function with "/pages/DocketEdit" once we have
    // completely switched to the new frontend pages
    private String redirectToEdit(String urlSuffix) {
        try {
            String referrer = FacesContext.getCurrentInstance().getExternalContext().getRequestHeaderMap()
                    .get("referer");
            String callerViewId = referrer.substring(referrer.lastIndexOf("/") + 1);
            if (!callerViewId.isEmpty() && callerViewId.contains("projects.jsf")) {
                return "/pages/editDocket" + urlSuffix;
            } else {
                return "/pages/DocketEdit" + urlSuffix;
            }
        } catch (NullPointerException e) {
            // This NPE gets thrown - and therefore must be caught - when "DocketForm" is
            // used from it's integration test
            // class "DocketFormIT", where no "FacesContext" is available!
            return "/pages/DocketEdit" + urlSuffix;
        }
    }


    // TODO:
    // replace calls to this function with "/pages/projects" once we have completely
    // switched to the new frontend pages
    private String redirectToList(String urlSuffix) {
        String referrer = FacesContext.getCurrentInstance().getExternalContext().getRequestHeaderMap().get("referer");
        String callerViewId = referrer.substring(referrer.lastIndexOf("/") + 1);
        if (!callerViewId.isEmpty() && callerViewId.contains("editDocket.jsf")) {
            return "/pages/projects" + urlSuffix;
        } else {
            return "/pages/DocketList" + urlSuffix;
        }
    }
}
