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
import de.sub.goobi.export.dms.ExportDms;
import de.sub.goobi.export.download.TiffHeader;
import de.sub.goobi.helper.BatchStepHelper;
import de.sub.goobi.helper.Helper;
import de.sub.goobi.helper.Page;
import de.sub.goobi.helper.WebDav;
import de.sub.goobi.metadaten.MetadatenSperrung;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;

import javax.annotation.PostConstruct;
import javax.enterprise.context.SessionScoped;
import javax.inject.Named;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.elasticsearch.search.sort.SortBuilders;
import org.elasticsearch.search.sort.SortOrder;
import org.kitodo.data.database.beans.Batch;
import org.kitodo.data.database.beans.Batch.Type;
import org.kitodo.data.database.beans.History;
import org.kitodo.data.database.beans.Process;
import org.kitodo.data.database.beans.Property;
import org.kitodo.data.database.beans.Task;
import org.kitodo.data.database.beans.User;
import org.kitodo.data.database.exceptions.DAOException;
import org.kitodo.data.database.helper.enums.HistoryTypeEnum;
import org.kitodo.data.database.helper.enums.TaskEditType;
import org.kitodo.data.database.helper.enums.TaskStatus;
import org.kitodo.data.exceptions.DataException;
import org.kitodo.dto.TaskDTO;
import org.kitodo.enums.ObjectMode;
import org.kitodo.model.LazyDTOModel;
import org.kitodo.services.ServiceManager;
import org.kitodo.workflow.Problem;
import org.kitodo.workflow.Solution;

@Named("AktuelleSchritteForm")
@SessionScoped
public class AktuelleSchritteForm extends BasisForm {
    private static final long serialVersionUID = 5841566727939692509L;
    private static final Logger logger = LogManager.getLogger(AktuelleSchritteForm.class);
    private Process myProcess = new Process();
    private Task mySchritt = new Task();
    private Problem problem = new Problem();
    private Solution solution = new Solution();
    private ObjectMode editMode = ObjectMode.NONE;
    private final WebDav myDav = new WebDav();
    private int gesamtAnzahlImages = 0;
    private int pageAnzahlImages = 0;
    private boolean nurOffeneSchritte = false;
    private boolean nurEigeneSchritte = false;
    private boolean showAutomaticTasks = false;
    private boolean hideCorrectionTasks = false;
    private HashMap<String, Boolean> anzeigeAnpassen;
    private String scriptPath;
    private String addToWikiField = "";
    private static String DONEDIRECTORYNAME = "fertig/";
    private BatchStepHelper batchHelper;
    private List<Property> properties;
    private Property property;
    private transient ServiceManager serviceManager = new ServiceManager();
    private int stepId;

    /**
     * Constructor.
     */
    public AktuelleSchritteForm() {
        super();
        super.setLazyDTOModel(new LazyDTOModel(serviceManager.getTaskService()));
        this.anzeigeAnpassen = new HashMap<>();
        this.anzeigeAnpassen.put("lockings", false);
        this.anzeigeAnpassen.put("selectionBoxes", false);
        this.anzeigeAnpassen.put("processId", false);
        this.anzeigeAnpassen.put("modules", false);
        this.anzeigeAnpassen.put("batchId", false);
        /*
         * Vorgangsdatum generell anzeigen?
         */
        LoginForm login = (LoginForm) Helper.getManagedBeanValue("#{LoginForm}");
        if (login != null && login.getMyBenutzer() != null) {
            this.anzeigeAnpassen.put("processDate", login.getMyBenutzer().isConfigProductionDateShow());
        } else {
            this.anzeigeAnpassen.put("processDate", false);
        }
        DONEDIRECTORYNAME = ConfigCore.getParameter("doneDirectoryName", "fertig/");
    }

    /**
     * Anzeige der Schritte.
     */
    public String filterAll() {
        try {
            List<TaskDTO> tasks;
            if (!showAutomaticTasks) {
                if (hideCorrectionTasks) {
                    tasks = serviceManager.getTaskService()
                            .findOpenNotAutomaticTasksWithoutCorrectionForCurrentUser(sortList());
                } else {
                    tasks = serviceManager.getTaskService().findOpenNotAutomaticTasksForCurrentUser(sortList());
                }
            } else {
                if (hideCorrectionTasks) {
                    tasks = serviceManager.getTaskService().findOpenTasksWithoutCorrectionForCurrentUser(sortList());
                } else {
                    tasks = serviceManager.getTaskService().findOpenTasksForCurrentUser(sortList());
                }
            }
            this.page = new Page<>(0, tasks);
        } catch (DataException e) {
            Helper.setFehlerMeldung("Error on reading ElasticSearch: ", e.getMessage());
            return null;
        }
        return "/pages/AktuelleSchritteAlle";
    }

    /**
     * This method initializes the task list without any filter whenever the
     * bean is created.
     */
    @PostConstruct
    public void initializeTaskList() {
        filterAll();
    }

    /**
     * It is possible that sorting by related object can be hard to achieve. Right
     * now it is replaced with sorting by id.
     *
     * @return sort clause for query
     */
    private String sortList() {
        String sort = SortBuilders.fieldSort("priority").order(SortOrder.ASC).toString();
        // TODO: find out if it's possible to sort by related objects
        // Order order = Order.asc("proc.title");
        if (this.sortierung.equals("schrittAsc")) {
            sort += ", " + SortBuilders.fieldSort("title").order(SortOrder.ASC).toString();
        }
        if (this.sortierung.equals("schrittDesc")) {
            sort += ", " + SortBuilders.fieldSort("title").order(SortOrder.DESC).toString();
        }
        if (this.sortierung.equals("prozessAsc")) {
            sort += ", " + SortBuilders.fieldSort("process").order(SortOrder.ASC).toString();
        }
        if (this.sortierung.equals("prozessDesc")) {
            sort += ", " + SortBuilders.fieldSort("process").order(SortOrder.DESC).toString();
        }
        /*if (this.sortierung.equals("batchAsc")) {
            order = Order.asc("proc.batchID");
        }
        if (this.sortierung.equals("batchDesc")) {
            order = Order.desc("proc.batchID");
        }
        if (this.sortierung.equals("prozessdateAsc")) {
            order = Order.asc("proc.creationDate");
        }
        if (this.sortierung.equals("prozessdateDesc")) {
            order = Order.desc("proc.creationDate");
        }*/
        if (this.sortierung.equals("projektAsc")) {
            sort += ", " + SortBuilders.fieldSort("project").order(SortOrder.ASC).toString();
        }
        if (this.sortierung.equals("projektDesc")) {
            sort += ", " + SortBuilders.fieldSort("project").order(SortOrder.DESC).toString();
        }
        if (this.sortierung.equals("modulesAsc")) {
            sort += ", " + SortBuilders.fieldSort("typeModuleName").order(SortOrder.ASC).toString();
        }
        if (this.sortierung.equals("modulesDesc")) {
            sort += ", " + SortBuilders.fieldSort("typeModuleName").order(SortOrder.DESC).toString();
        }
        if (this.sortierung.equals("statusAsc")) {
            sort += ", " + SortBuilders.fieldSort("processingStatus").order(SortOrder.ASC).toString();
        }
        if (this.sortierung.equals("statusDesc")) {
            sort += ", " + SortBuilders.fieldSort("processingStatus").order(SortOrder.DESC).toString();
        }
        return sort;
    }

    /**
     * Bearbeitung des Schritts übernehmen oder abschliessen.
     */
    public String schrittDurchBenutzerUebernehmen() {
        Helper.getHibernateSession().refresh(this.mySchritt);

        if (this.mySchritt.getProcessingStatusEnum() != TaskStatus.OPEN) {
            Helper.setFehlerMeldung("stepInWorkError");
            return null;
        } else {
            this.setMySchritt(serviceManager.getWorkflowService().assignTaskToUser(this.getMySchritt()));
        }
        return "/pages/AktuelleSchritteBearbeiten";
    }

    /**
     * Edit task.
     *
     * @return page
     */
    public String editStep() {

        Helper.getHibernateSession().refresh(mySchritt);

        return "/pages/AktuelleSchritteBearbeiten";
    }

    /**
     * Take over batch.
     *
     * @return page
     */
    public String takeOverBatch() {
        // find all steps with same batch id and step status
        List<Task> currentStepsOfBatch;
        String taskTitle = this.mySchritt.getTitle();
        List<Batch> batches = serviceManager.getProcessService().getBatchesByType(mySchritt.getProcess(),
                Type.LOGISTIC);
        if (batches.size() > 1) {
            Helper.setFehlerMeldung("multipleBatchesAssigned");
            return null;
        }
        if (batches.size() != 0) {
            Integer batchNumber = batches.iterator().next().getId();
            // only steps with same title
            currentStepsOfBatch = serviceManager.getTaskService().getCurrentTasksOfBatch(taskTitle, batchNumber);
        } else {
            return schrittDurchBenutzerUebernehmen();
        }
        // if only one step is assigned for this batch, use the single

        // Helper.setMeldung("found " + currentStepsOfBatch.size() + " elements
        // in batch");
        if (currentStepsOfBatch.size() == 0) {
            return null;
        }
        if (currentStepsOfBatch.size() == 1) {
            return schrittDurchBenutzerUebernehmen();
        }

        for (Task s : currentStepsOfBatch) {
            if (s.getProcessingStatusEnum().equals(TaskStatus.OPEN)) {
                s.setProcessingStatusEnum(TaskStatus.INWORK);
                s.setEditTypeEnum(TaskEditType.MANUAL_MULTI);
                s.setProcessingTime(new Date());
                User ben = (User) Helper.getManagedBeanValue("#{LoginForm.myBenutzer}");
                if (ben != null) {
                    s.setProcessingUser(ben);
                }
                if (s.getProcessingBegin() == null) {
                    Date myDate = new Date();
                    s.setProcessingBegin(myDate);
                }
                s.getProcess().getHistory().add(new History(s.getProcessingBegin(), s.getOrdering().doubleValue(),
                        s.getTitle(), HistoryTypeEnum.taskInWork, s.getProcess()));

                if (s.isTypeImagesRead() || s.isTypeImagesWrite()) {
                    try {
                        new File(serviceManager.getProcessService().getImagesOrigDirectory(false, s.getProcess()));
                    } catch (Exception e1) {
                        logger.error(e1);
                    }
                    s.setProcessingTime(new Date());

                    if (ben != null) {
                        s.setProcessingUser(ben);
                    }
                    this.myDav.downloadToHome(s.getProcess(), !s.isTypeImagesWrite());

                }
            }

            try {
                this.serviceManager.getProcessService().save(s.getProcess());
            } catch (DataException e) {
                Helper.setFehlerMeldung(Helper.getTranslation("stepSaveError"), e);
                logger.error("Task couldn't get saved", e);
            }
        }

        this.setBatchHelper(new BatchStepHelper(currentStepsOfBatch));
        return "/pages/batchesEdit";
    }

    /**
     * Edit batch.
     *
     * @return page
     */
    public String batchesEdit() {
        // find all steps with same batch id and step status
        List<Task> currentStepsOfBatch;
        String taskTitle = this.mySchritt.getTitle();
        List<Batch> batches = serviceManager.getProcessService().getBatchesByType(mySchritt.getProcess(),
                Type.LOGISTIC);
        if (batches.size() > 1) {
            Helper.setFehlerMeldung("multipleBatchesAssigned");
            return null;
        }
        if (batches.size() != 0) {
            Integer batchNumber = batches.iterator().next().getId();
            // only steps with same title
            currentStepsOfBatch = serviceManager.getTaskService().getCurrentTasksOfBatch(taskTitle, batchNumber);
        } else {
            return "/pages/AktuelleSchritteBearbeiten";
        }
        // if only one step is assigned for this batch, use the single

        // Helper.setMeldung("found " + currentStepsOfBatch.size() + " elements
        // in batch");

        if (currentStepsOfBatch.size() == 1) {
            return "/pages/AktuelleSchritteBearbeiten";
        }
        this.setBatchHelper(new BatchStepHelper(currentStepsOfBatch));
        return "/pages/batchesEdit";
    }

    /**
     * Not sure.
     *
     * @return page
     */
    public String schrittDurchBenutzerZurueckgeben() {
        try {
            setMySchritt(serviceManager.getWorkflowService().unassignTaskFromUser(this.mySchritt));
        } catch (DataException e) {
            logger.error("Task couldn't get saved/inserted", e);
        }

        return filterAll();
    }

    /**
     * Not sure.
     *
     * @return page
     */
    public String schrittDurchBenutzerAbschliessen() throws DataException, IOException {
        setMySchritt(serviceManager.getWorkflowService().closeTaskByUser(this.mySchritt));
        return filterAll();
    }

    public String sperrungAufheben() {
        MetadatenSperrung.unlockProcess(this.mySchritt.getProcess().getId());
        return null;
    }

    /**
     * Korrekturmeldung an vorherige Schritte.
     */
    public List<Task> getPreviousStepsForProblemReporting() {
        return serviceManager.getTaskService().getPreviousTasksForProblemReporting(this.mySchritt.getOrdering(),
                this.mySchritt.getProcess().getId());
    }

    public int getSizeOfPreviousStepsForProblemReporting() {
        return getPreviousStepsForProblemReporting().size();
    }

    /**
     * Report the problem.
     *
     * @return problem as String
     */
    public String reportProblem() {
        serviceManager.getWorkflowService().setProblem(getProblem());
        try {
            setMySchritt(serviceManager.getWorkflowService().reportProblem(this.mySchritt));
        } catch (DAOException | DataException e) {
            logger.error("Problem couldn't be reported: " + e);
        }
        setProblem(serviceManager.getWorkflowService().getProblem());
        return filterAll();
    }

    /**
     * Problem-behoben-Meldung an nachfolgende Schritte.
     */
    public List<Task> getNextStepsForProblemSolution() {
        return serviceManager.getTaskService().getNextTasksForProblemSolution(this.mySchritt.getOrdering(),
                this.mySchritt.getProcess().getId());
    }

    public int getSizeOfNextStepsForProblemSolution() {
        return getNextStepsForProblemSolution().size();
    }

    /**
     * Solve problem.
     *
     * @return String
     */
    public String solveProblem() {
        serviceManager.getWorkflowService().setSolution(getSolution());
        try {
            setMySchritt(serviceManager.getWorkflowService().solveProblem(this.mySchritt));
        } catch (DAOException | DataException e) {
            logger.error("Problem couldn't be solved: " + e);
        }
        setSolution(serviceManager.getWorkflowService().getSolution());
        return filterAll();
    }

    /**
     * Upload from home.
     *
     * @return String
     */
    @SuppressWarnings("unchecked")
    public String uploadFromHomeAlle() throws NumberFormatException, DataException, IOException {
        List<URI> fertigListe = this.myDav.uploadAllFromHome(DONEDIRECTORYNAME);
        List<URI> geprueft = new ArrayList<>();
        /*
         * die hochgeladenen Prozess-IDs durchlaufen und auf abgeschlossen setzen
         */
        if (fertigListe.size() > 0 && this.nurOffeneSchritte) {
            this.nurOffeneSchritte = false;
            filterAll();
        }
        for (URI element : fertigListe) {
            String myID = element.toString()
                    .substring(element.toString().indexOf("[") + 1, element.toString().indexOf("]")).trim();

            for (Task step : (Iterable<Task>) this.page.getCompleteList()) {
                /*
                 * nur wenn der Schritt bereits im Bearbeitungsmodus ist, abschliessen
                 */
                if (step.getProcess().getId() == Integer.parseInt(myID)
                        && step.getProcessingStatusEnum() == TaskStatus.INWORK) {
                    this.mySchritt = step;
                    if (!schrittDurchBenutzerAbschliessen().isEmpty()) {
                        geprueft.add(element);
                    }
                    this.mySchritt.setEditTypeEnum(TaskEditType.MANUAL_MULTI);
                }
            }
        }

        this.myDav.removeAllFromHome(geprueft, URI.create(DONEDIRECTORYNAME));
        Helper.setMeldung(null, "removed " + geprueft.size() + " directories from user home:", DONEDIRECTORYNAME);
        return null;
    }

    /**
     * Download to home page.
     *
     * @return String
     */
    public String downloadToHomePage() {
        download();
        // calcHomeImages();
        Helper.setMeldung(null, "Created directies in user home", "");
        return null;
    }

    /**
     * Download to home.
     *
     * @return String
     */
    public String downloadToHomeHits() {
        download();
        // calcHomeImages();
        Helper.setMeldung(null, "Created directories in user home", "");
        return null;
    }

    @SuppressWarnings("unchecked")
    private void download() {
        for (TaskDTO taskDTO : (List<TaskDTO>) this.page.getListReload()) {
            Task task = new Task();
            try {
                task = serviceManager.getTaskService().getById(taskDTO.getId());
            } catch (DAOException e) {
                logger.error(e);
            }
            if (task.getProcessingStatusEnum() == TaskStatus.OPEN) {
                task.setProcessingStatusEnum(TaskStatus.INWORK);
                task.setEditTypeEnum(TaskEditType.MANUAL_MULTI);
                mySchritt.setProcessingTime(new Date());
                User ben = (User) Helper.getManagedBeanValue("#{LoginForm.myBenutzer}");
                if (ben != null) {
                    mySchritt.setProcessingUser(ben);
                }
                task.setProcessingBegin(new Date());
                Process proz = task.getProcess();
                try {
                    this.serviceManager.getProcessService().save(proz);
                } catch (DataException e) {
                    Helper.setMeldung("fehlerNichtSpeicherbar" + proz.getTitle());
                }
                this.myDav.downloadToHome(proz, false);
            }
        }
    }

    public String getScriptPath() {
        return this.scriptPath;
    }

    public void setScriptPath(String scriptPath) {
        this.scriptPath = scriptPath;
    }

    /**
     * Execute script.
     */
    public void executeScript() throws DAOException, DataException {
        Task task = serviceManager.getTaskService().getById(this.mySchritt.getId());
        serviceManager.getTaskService().executeScript(task, this.scriptPath, false);
    }

    @Deprecated
    public int getHomeBaende() {
        return 0;
    }

    public int getAllImages() {
        return this.gesamtAnzahlImages;
    }

    public int getPageImages() {
        return this.pageAnzahlImages;
    }

    /**
     * Calc home images.
     */
    @SuppressWarnings("unchecked")
    public void calcHomeImages() {
        this.gesamtAnzahlImages = 0;
        this.pageAnzahlImages = 0;
        User aktuellerBenutzer = (User) Helper.getManagedBeanValue("#{LoginForm.myBenutzer}");
        if (aktuellerBenutzer != null && aktuellerBenutzer.isWithMassDownload()) {
            for (TaskDTO taskDTO : (List<TaskDTO>) this.page.getCompleteList()) {
                try {
                    Task task = serviceManager.getTaskService().getById(taskDTO.getId());
                    if (task.getProcessingStatusEnum() == TaskStatus.OPEN) {
                        // gesamtAnzahlImages +=
                        // myDav.getAnzahlImages(step.getProzess().getImagesOrigDirectory());
                        this.gesamtAnzahlImages += serviceManager.getFileService().getSubUris(
                                serviceManager.getProcessService().getImagesOrigDirectory(false, task.getProcess()))
                                .size();
                    }
                } catch (DAOException | IOException e) {
                    logger.error(e);
                }
            }
        }
    }

    /**
     * Get my task.
     *
     * @return task
     */
    public Task getMySchritt() {
        try {
            schrittPerParameterLaden();
        } catch (DAOException | NumberFormatException e) {
            logger.error(e);
        }
        return this.mySchritt;
    }

    /**
     * Set my task with edit mode set to empty String.
     *
     * @param task
     *            Object
     */
    public void setMySchritt(Task task) {
        this.editMode = ObjectMode.NONE;
        setStep(task);
    }

    /**
     * Set my task.
     *
     * @param task
     *            Object
     */
    public void setStep(Task task) {
        this.mySchritt = task;
        this.mySchritt.setLocalizedTitle(serviceManager.getTaskService().getLocalizedTitle(task.getTitle()));
        this.myProcess = this.mySchritt.getProcess();
        loadProcessProperties();
        setAttributesForProcess();
    }

    public Task getStep() {
        return this.mySchritt;
    }

    /**
     * Get mode for edition.
     *
     * @return mode for edition as ObjectMode objects
     */
    public ObjectMode getEditMode() {
        return this.editMode;
    }

    /**
     * Set mode for edition.
     *
     * @param editMode
     *            mode for edition as ObjectMode objects
     */
    public void setEditMode(ObjectMode editMode) {
        this.editMode = editMode;
    }

    /**
     * Get problem.
     *
     * @return Problem object
     */
    public Problem getProblem() {
        return problem;
    }

    /**
     * Set problem.
     *
     * @param problem
     *            object
     */
    public void setProblem(Problem problem) {
        this.problem = problem;
    }

    /**
     * Get solution.
     *
     * @return Solution object
     */
    public Solution getSolution() {
        return solution;
    }

    /**
     * Set solution.
     *
     * @param solution
     *            object
     */
    public void setSolution(Solution solution) {
        this.solution = solution;
    }

    private void setAttributesForProcess() {
        Process process = this.mySchritt.getProcess();
        process.setBlockedUser(serviceManager.getProcessService().getBlockedUser(process));
        process.setBlockedMinutes(serviceManager.getProcessService().getBlockedMinutes(process));
        process.setBlockedSeconds(serviceManager.getProcessService().getBlockedSeconds(process));
    }

    /**
     * prüfen, ob per Parameter vielleicht zunÃ¤chst ein anderer geladen werden
     * soll.
     *
     * @throws DAOException
     *             , NumberFormatException
     */
    private void schrittPerParameterLaden() throws DAOException, NumberFormatException {
        String param = Helper.getRequestParameter("myid");
        if (param != null && !param.equals("")) {
            /*
             * wenn bisher noch keine aktuellen Schritte ermittelt wurden, dann dies jetzt
             * nachholen, damit die Liste vollstÃ¤ndig ist
             */
            if (this.page == null && Helper.getManagedBeanValue("#{LoginForm.myBenutzer}") != null) {
                filterAll();
            }
            Integer inParam = Integer.valueOf(param);
            if (this.mySchritt == null || this.mySchritt.getId() == null || !this.mySchritt.getId().equals(inParam)) {
                this.mySchritt = serviceManager.getTaskService().getById(inParam);
            }
        }
    }

    /**
     * Auswahl mittels Selectboxen.
     */
    @SuppressWarnings("unchecked")
    public void selectionAll() {
        for (TaskDTO task : (List<TaskDTO>) this.page.getList()) {
            task.setSelected(true);
        }
    }

    /**
     * Selection none.
     */
    @SuppressWarnings("unchecked")
    public void selectionNone() {
        for (TaskDTO task : (List<TaskDTO>) this.page.getList()) {
            task.setSelected(false);
        }
    }

    /**
     * Downloads.
     */
    public void downloadTiffHeader() throws IOException {
        TiffHeader tiff = new TiffHeader(this.mySchritt.getProcess());
        tiff.exportStart();
    }

    /**
     * Export DMS.
     */
    public void exportDMS() {
        ExportDms export = new ExportDms();
        try {
            export.startExport(this.mySchritt.getProcess());
        } catch (Exception e) {
            Helper.setFehlerMeldung("Error on export", e.getMessage());
            logger.error(e);
        }
    }

    public boolean isNurOffeneSchritte() {
        return this.nurOffeneSchritte;
    }

    public void setNurOffeneSchritte(boolean nurOffeneSchritte) {
        this.nurOffeneSchritte = nurOffeneSchritte;
    }

    public boolean isNurEigeneSchritte() {
        return this.nurEigeneSchritte;
    }

    public void setNurEigeneSchritte(boolean nurEigeneSchritte) {
        this.nurEigeneSchritte = nurEigeneSchritte;
    }

    public HashMap<String, Boolean> getAnzeigeAnpassen() {
        return this.anzeigeAnpassen;
    }

    public void setAnzeigeAnpassen(HashMap<String, Boolean> anzeigeAnpassen) {
        this.anzeigeAnpassen = anzeigeAnpassen;
    }

    /**
     * Get Wiki field.
     *
     * @return values for wiki field
     */
    public String getWikiField() {
        return this.mySchritt.getProcess().getWikiField();

    }

    /**
     * Sets new value for wiki field.
     *
     * @param inString
     *            input String
     */
    public void setWikiField(String inString) {
        this.mySchritt.getProcess().setWikiField(inString);
    }

    public String getAddToWikiField() {
        return this.addToWikiField;
    }

    public void setAddToWikiField(String addToWikiField) {
        this.addToWikiField = addToWikiField;
    }

    /**
     * Add to wiki field.
     */
    public void addToWikiField() {
        if (addToWikiField != null && addToWikiField.length() > 0) {
            // TODO: why this not used variable is here?
            User user = (User) Helper.getManagedBeanValue("#{LoginForm.myBenutzer}");
            this.mySchritt.setProcess(serviceManager.getProcessService().addToWikiField(this.addToWikiField,
                    this.mySchritt.getProcess()));
            this.addToWikiField = "";
            try {
                this.serviceManager.getProcessService().save(this.mySchritt.getProcess());
            } catch (DataException e) {
                logger.error(e);
            }
        }
    }

    /**
     * Get property for process.
     *
     * @return property for process
     */
    public Property getProperty() {
        return this.property;
    }

    /**
     * Set property for process.
     * 
     * @param property
     *            for process as Property object
     */
    public void setProperty(Property property) {
        this.property = property;
    }

    /**
     * Get list of process properties.
     * 
     * @return list of process properties
     */
    public List<Property> getProperties() {
        return this.properties;
    }

    /**
     * Set list of process properties.
     * 
     * @param properties
     *            for process as Property objects
     */
    public void setProperties(List<Property> properties) {
        this.properties = properties;
    }

    /**
     * Get size of properties' list.
     * 
     * @return size of properties' list
     */
    public int getPropertiesSize() {
        return this.properties.size();
    }

    private void loadProcessProperties() {
        serviceManager.getProcessService().refresh(this.myProcess);
        setProperties(this.myProcess.getProperties());
    }

    /**
     * Save current property.
     */
    public void saveCurrentProperty() {
        try {
            serviceManager.getPropertyService().save(this.property);
            if (!this.myProcess.getProperties().contains(this.property)) {
                this.myProcess.getProperties().add(this.property);
            }
            serviceManager.getProcessService().save(this.myProcess);
            Helper.setMeldung("propertiesSaved");
        } catch (DataException e) {
            logger.error(e);
            Helper.setFehlerMeldung("propertiesNotSaved");
        }
        loadProcessProperties();
    }

    /**
     * Duplicate property.
     */
    public void duplicateProperty() {
        Property newProperty = serviceManager.getPropertyService().transfer(this.property);
        try {
            newProperty.getProcesses().add(this.myProcess);
            this.myProcess.getProperties().add(newProperty);
            serviceManager.getPropertyService().save(newProperty);
            Helper.setMeldung("propertySaved");
        } catch (DataException e) {
            logger.error(e);
            Helper.setFehlerMeldung("propertiesNotSaved");
        }
        loadProcessProperties();
    }

    /**
     * Get batch helper.
     * 
     * @return batch helper as BatchHelper object
     */
    public BatchStepHelper getBatchHelper() {
        return this.batchHelper;
    }

    /**
     * Set batch helper.
     * 
     * @param batchHelper
     *            as BatchHelper object
     */
    public void setBatchHelper(BatchStepHelper batchHelper) {
        this.batchHelper = batchHelper;
    }

    public boolean getShowAutomaticTasks() {
        return this.showAutomaticTasks;
    }

    public void setShowAutomaticTasks(boolean showAutomaticTasks) {
        this.showAutomaticTasks = showAutomaticTasks;
    }

    public boolean getHideCorrectionTasks() {
        return hideCorrectionTasks;
    }

    public void setHideCorrectionTasks(boolean hideCorrectionTasks) {
        this.hideCorrectionTasks = hideCorrectionTasks;
    }

    /**
     * Return the id of the current task.
     *
     * @return int stepId
     */
    public int getStepId() {
        return this.stepId;
    }

    /**
     * Set the id of the current task.
     *
     * @param stepId
     *            as int
     */
    public void setStepId(int stepId) {
        this.stepId = stepId;
    }

    /**
     * Method being used as viewAction for AktuelleSchritteForm.
     */
    public void loadMyStep() {
        try {
            if (!Objects.equals(this.stepId, null)) {
                setMySchritt(this.serviceManager.getTaskService().getById(this.stepId));
            }
        } catch (DAOException e) {
            Helper.setFehlerMeldung("Error retrieving task with ID '" + this.stepId + "'; ", e.getMessage());
        }
    }
}
