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

package org.kitodo.services.workflow;

import de.sub.goobi.config.ConfigCore;
import de.sub.goobi.helper.Helper;
import de.sub.goobi.helper.WebDav;
import de.sub.goobi.helper.tasks.TaskManager;
import de.sub.goobi.metadaten.MetadatenImagesHelper;

import java.io.IOException;
import java.net.URI;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.locks.ReentrantLock;

import javax.naming.AuthenticationException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.goobi.production.cli.helper.WikiFieldHelper;
import org.goobi.production.flow.jobs.HistoryAnalyserJob;
import org.kitodo.data.database.beans.History;
import org.kitodo.data.database.beans.Process;
import org.kitodo.data.database.beans.Property;
import org.kitodo.data.database.beans.Task;
import org.kitodo.data.database.beans.User;
import org.kitodo.data.database.exceptions.DAOException;
import org.kitodo.data.database.helper.enums.HistoryTypeEnum;
import org.kitodo.data.database.helper.enums.PropertyType;
import org.kitodo.data.database.helper.enums.TaskEditType;
import org.kitodo.data.database.helper.enums.TaskStatus;
import org.kitodo.data.exceptions.DataException;
import org.kitodo.production.thread.TaskScriptThread;
import org.kitodo.services.ServiceManager;
import org.kitodo.workflow.Problem;
import org.kitodo.workflow.Solution;

public class WorkflowService {

    private int openTasksWithTheSameOrdering;
    private List<Task> automaticTasks;
    private List<Task> tasksToFinish;
    private Problem problem = new Problem();
    private Solution solution = new Solution();
    private User user = (User) Helper.getManagedBeanValue("#{LoginForm.myBenutzer}");
    private Boolean flagWait = false;
    private final ReentrantLock flagWaitLock = new ReentrantLock();
    private final SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    private final WebDav webDav = new WebDav();
    private static final Logger logger = LogManager.getLogger(WorkflowService.class);
    private static WorkflowService instance = null;
    private transient ServiceManager serviceManager = new ServiceManager();

    /**
     * Return singleton variable of type TaskService.
     *
     * @return unique instance of TaskService
     */
    public static WorkflowService getInstance() {
        if (Objects.equals(instance, null)) {
            synchronized (WorkflowService.class) {
                if (Objects.equals(instance, null)) {
                    instance = new WorkflowService();
                }
            }
        }
        return instance;
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

    /**
     * Set user for test purpose.
     *
     * @param user as User object
     */
    public void setUser(User user) {
        this.user = user;
    }

    /**
     * Set Task status up.
     *
     * @param task
     *            to change status up
     * @return updated task
     */
    public Task setTaskStatusUp(Task task) throws DataException, IOException {
        if (task.getProcessingStatusEnum() != TaskStatus.DONE) {
            task = serviceManager.getTaskService().setProcessingStatusUp(task);
            task.setEditTypeEnum(TaskEditType.ADMIN);
            if (task.getProcessingStatusEnum() == TaskStatus.DONE) {
                close(task);
            } else {
                task.setProcessingTime(new Date());
                if (this.user != null) {
                    task.setProcessingUser(this.user);
                }
            }
        }
        return task;
    }

    /**
     * Change Task status down.
     *
     * @param task
     *            to change status down
     * @return updated task
     */
    public Task setTaskStatusDown(Task task) {
        task.setEditTypeEnum(TaskEditType.ADMIN);
        task.setProcessingTime(new Date());
        if (this.user != null) {
            task.setProcessingUser(this.user);
        }
        return serviceManager.getTaskService().setProcessingStatusDown(task);
    }

    /**
     * Change Task status up for list of tasks assigned to given Process.
     * 
     * @param process
     *            object
     */
    public void setTasksStatusUp(Process process) throws DataException, IOException {
        List<Task> tasks = process.getTasks();

        for (Task task : tasks) {
            if (!task.getProcessingStatus().equals(TaskStatus.DONE.getValue())) {
                task.setProcessingStatus(task.getProcessingStatus() + 1);
                task.setEditType(TaskEditType.ADMIN.getValue());
                if (task.getProcessingStatus().equals(TaskStatus.DONE.getValue())) {
                    close(task);
                } else {
                    if (this.user != null) {
                        task.setProcessingUser(this.user);
                        serviceManager.getTaskService().save(task);
                    }
                }
                break;
            }
        }
    }

    /**
     * Change Task status down for list of tasks assigned to given Process.
     * 
     * @param process
     *            object
     */
    public void setTasksStatusDown(Process process) throws DataException {
        List<Task> tasks = new ArrayList<>(process.getTasks());
        Collections.reverse(tasks);

        for (Task task : tasks) {
            //TODO: check if this behaviour is correct
            if (process.getTasks().get(0) != task && task.getProcessingStatusEnum() != TaskStatus.LOCKED) {
                task.setEditTypeEnum(TaskEditType.ADMIN);
                task.setProcessingTime(new Date());
                if (this.user != null) {
                    task.setProcessingUser(this.user);
                }
                task = serviceManager.getTaskService().setProcessingStatusDown(task);
                serviceManager.getTaskService().save(task);
                break;
            }
        }
    }

    /**
     * Not sure.
     *
     * @param task
     *            object
     * @return closed Task
     */
    public Task closeTaskByUser(Task task) throws DataException, IOException {

        // if step allows writing of images, then count all images here
        if (task.isTypeImagesWrite()) {
            try {
                HistoryAnalyserJob.updateHistory(task.getProcess());
            } catch (Exception e) {
                Helper.setFehlerMeldung("Error while calculation of storage and images", e);
            }
        }

        // if the result of the task is to be verified first, then if necessary, cancel
        // the completion
        if (task.isTypeCloseVerify()) {
            // metadata validation
            if (task.isTypeMetadata() && ConfigCore.getBooleanParameter("useMetadatenvalidierung")) {
                serviceManager.getMetadataValidationService().setAutoSave(true);
                if (!serviceManager.getMetadataValidationService().validate(task.getProcess())) {
                    return null;
                }
            }

            // image validation
            if (task.isTypeImagesWrite()) {
                MetadatenImagesHelper mih = new MetadatenImagesHelper(null, null);
                try {
                    if (!mih.checkIfImagesValid(task.getProcess().getTitle(),
                        serviceManager.getProcessService().getImagesOrigDirectory(false, task.getProcess()))) {
                        return null;
                    }
                } catch (Exception e) {
                    Helper.setFehlerMeldung("Error on image validation: ", e);
                }
            }
        }
        // if the result of the verification is ok, then continue, otherwise it is not
        // reached
        this.webDav.uploadFromHome(task.getProcess());
        task.setEditTypeEnum(TaskEditType.MANUAL_SINGLE);
        close(task);
        return task;
    }

    /**
     * Close task.
     *
     * @param task
     *            as Task object
     */
    public void close(Task task) throws DataException, IOException {
        task.setProcessingStatus(3);
        task.setProcessingTime(new Date());
        task.setProcessingUser(this.user);
        task.setProcessingEnd(new Date());

        serviceManager.getTaskService().save(task);
        automaticTasks = new ArrayList<>();
        tasksToFinish = new ArrayList<>();

        History history = new History(new Date(), task.getOrdering(), task.getTitle(), HistoryTypeEnum.taskDone,
                task.getProcess());
        serviceManager.getHistoryService().save(history);

        // check if there are tasks that take place in parallel but are not yet
        // completed
        List<Task> tasks = task.getProcess().getTasks();
        List<Task> allHigherTasks = getAllHigherTasks(tasks, task);

        activateNextTask(allHigherTasks);

        Process process = task.getProcess();
        URI imagesOrigDirectory = serviceManager.getProcessService().getImagesOrigDirectory(true, process);
        Integer numberOfFiles = serviceManager.getFileService().getNumberOfFiles(imagesOrigDirectory);
        if (!process.getSortHelperImages().equals(numberOfFiles)) {
            process.setSortHelperImages(numberOfFiles);
            serviceManager.getProcessService().save(process);
        }

        updateProcessSortHelperStatus(process);

        for (Task automaticTask : automaticTasks) {
            TaskScriptThread thread = new TaskScriptThread(automaticTask);
            TaskManager.addTask(thread);
        }
        for (Task finish : tasksToFinish) {
            close(finish);
        }
    }

    /**
     * Taken from AktuelleSchritteForm.
     *
     * @param task
     *            object
     * @return Task object
     */
    public Task assignTaskToUser(Task task) {
        this.flagWaitLock.lock();
        try {
            if (!this.flagWait) {
                this.flagWait = true;

                task.setProcessingStatusEnum(TaskStatus.INWORK);
                task.setEditTypeEnum(TaskEditType.MANUAL_SINGLE);
                task.setProcessingTime(new Date());
                if (this.user != null) {
                    task.setProcessingUser(this.user);
                }
                if (task.getProcessingBegin() == null) {
                    task.setProcessingBegin(new Date());
                }
                task.getProcess().getHistory()
                        .add(new History(task.getProcessingBegin(), task.getOrdering().doubleValue(), task.getTitle(),
                                HistoryTypeEnum.taskInWork, task.getProcess()));

                updateProcessSortHelperStatus(task.getProcess());

                // if it is an image task, then download the images into the user home directory
                if (task.isTypeImagesRead() || task.isTypeImagesWrite()) {
                    downloadToHome(task);
                }
            } else {
                Helper.setFehlerMeldung("stepInWorkError");
            }
            this.flagWait = false;
        } catch (DataException e) {
            Helper.setFehlerMeldung(Helper.getTranslation("stepSaveError"), e);
            logger.error("Task couldn't get saved", e);
        } finally {
            this.flagWaitLock.unlock();
        }
        return task;
    }

    /**
     * Unassing user from task.
     * 
     * @param task
     *            object
     * @return Task object
     */
    public Task unassignTaskFromUser(Task task) throws DataException {
        this.webDav.uploadFromHome(task.getProcess());
        task.setProcessingStatusEnum(TaskStatus.OPEN);
        task.setProcessingUser(null);
        // if we have a correction task here then never remove startdate
        if (serviceManager.getTaskService().isCorrectionStep(task)) {
            task.setProcessingBegin(null);
        }
        task.setEditTypeEnum(TaskEditType.MANUAL_SINGLE);
        task.setProcessingTime(new Date());

        updateProcessSortHelperStatus(task.getProcess());

        return task;
    }

    /**
     * Report the problem.
     *
     * @param currentTask
     *            as Task object
     * @return Task
     */
    public Task reportProblem(Task currentTask) throws DAOException, DataException {
        this.webDav.uploadFromHome(this.user, currentTask.getProcess());
        Date date = new Date();
        currentTask.setProcessingStatusEnum(TaskStatus.LOCKED);
        currentTask.setEditTypeEnum(TaskEditType.MANUAL_SINGLE);
        currentTask.setProcessingTime(date);
        currentTask.setProcessingUser(this.user);
        currentTask.setProcessingBegin(null);

        Task correctionTask = serviceManager.getTaskService().getById(this.problem.getId());
        correctionTask.setProcessingStatusEnum(TaskStatus.OPEN);
        correctionTask = serviceManager.getTaskService().setCorrectionStep(correctionTask);
        correctionTask.setProcessingEnd(null);

        Property processProperty = prepareProblemMessageProperty(date);
        processProperty.getProcesses().add(currentTask.getProcess());
        currentTask.getProcess().getProperties().add(processProperty);

        currentTask.getProcess().setWikiField(prepareProblemWikiField(currentTask.getProcess(), correctionTask));

        serviceManager.getTaskService().save(correctionTask);
        currentTask.getProcess().getHistory().add(new History(date, correctionTask.getOrdering().doubleValue(),
                correctionTask.getTitle(), HistoryTypeEnum.taskError, correctionTask.getProcess()));

        closeTasksBetweenCurrentAndCorrectionTask(currentTask, correctionTask);

        updateProcessSortHelperStatus(currentTask.getProcess());

        this.problem.setMessage("");
        this.problem.setId(0);
        return currentTask;
    }

    /**
     * This one is taken out of BatchStepHelper.
     *
     * @param currentTask
     *            as Task object
     * @param problemTask
     *            title of problem task as String
     */
    public Task reportProblem(Task currentTask, String problemTask) throws DataException {
        Date date = new Date();
        currentTask.setProcessingStatusEnum(TaskStatus.LOCKED);
        currentTask.setEditTypeEnum(TaskEditType.MANUAL_SINGLE);
        currentTask.setProcessingTime(date);
        currentTask.setProcessingUser(this.user);
        currentTask.setProcessingBegin(null);

        Task correctionTask = null;
        for (Task task : currentTask.getProcess().getTasks()) {
            if (task.getTitle().equals(problemTask)) {
                correctionTask = task;
            }
        }
        if (correctionTask != null) {
            correctionTask.setProcessingStatusEnum(TaskStatus.OPEN);
            correctionTask = serviceManager.getTaskService().setCorrectionStep(correctionTask);
            correctionTask.setProcessingEnd(null);

            Property processProperty = prepareProblemMessageProperty(date);
            processProperty.getProcesses().add(currentTask.getProcess());
            currentTask.getProcess().getProperties().add(processProperty);

            currentTask.getProcess().setWikiField(prepareProblemWikiField(currentTask.getProcess(), correctionTask));

            this.serviceManager.getTaskService().save(correctionTask);
            currentTask.getProcess().getHistory().add(new History(date, correctionTask.getOrdering().doubleValue(),
                    correctionTask.getTitle(), HistoryTypeEnum.taskError, correctionTask.getProcess()));

            closeTasksBetweenCurrentAndCorrectionTask(currentTask, correctionTask);

            updateProcessSortHelperStatus(currentTask.getProcess());
        }

        return currentTask;
    }

    /**
     * Solve problem. This one is taken from AktuelleSchritteForm.
     *
     * @param currentTask
     *            as Task object
     * @return Task
     */
    public Task solveProblem(Task currentTask) throws DAOException, DataException {
        Date date = new Date();
        this.webDav.uploadFromHome(currentTask.getProcess());
        currentTask.setProcessingStatusEnum(TaskStatus.DONE);
        currentTask.setProcessingEnd(date);
        currentTask.setEditTypeEnum(TaskEditType.MANUAL_SINGLE);
        currentTask.setProcessingTime(new Date());
        currentTask.setProcessingUser(this.user);

        Task correctionTask = serviceManager.getTaskService().getById(this.solution.getId());

        closeTasksBetweenCurrentAndCorrectionTaskA(currentTask, correctionTask, date);

        currentTask.getProcess().setWikiField(prepareSolutionWikiField(currentTask.getProcess(), correctionTask));

        Property processProperty = prepareSolveMessageProperty(correctionTask);
        processProperty.getProcesses().add(currentTask.getProcess());
        currentTask.getProcess().getProperties().add(processProperty);

        updateProcessSortHelperStatus(currentTask.getProcess());

        this.solution.setMessage("");
        this.solution.setId(0);
        return currentTask;
    }

    /**
     * This one is taken out of BatchStepHelper.
     *
     * @param currentTask
     *            as Task object
     * @param solutionTask
     *            title of problematic task as String
     */
    public Task solveProblem(Task currentTask, String solutionTask) throws AuthenticationException, DataException {
        if (this.user == null) {
            // TODO: this one is removed in next PR
            throw new AuthenticationException("userNotFound");
        }
        Date date = new Date();
        this.webDav.uploadFromHome(currentTask.getProcess());
        currentTask.setProcessingStatusEnum(TaskStatus.DONE);
        currentTask.setProcessingEnd(date);
        currentTask.setEditTypeEnum(TaskEditType.MANUAL_SINGLE);
        currentTask.setProcessingTime(date);
        currentTask.setProcessingUser(this.user);

        Task correctionTask = null;
        for (Task task : currentTask.getProcess().getTasks()) {
            if (task.getTitle().equals(solutionTask)) {
                correctionTask = task;
            }
        }
        if (correctionTask != null) {
            closeTasksBetweenCurrentAndCorrectionTaskB(currentTask, correctionTask, date);

            Property processProperty = prepareSolveMessageProperty(correctionTask);
            processProperty.getProcesses().add(currentTask.getProcess());
            currentTask.getProcess().getProperties().add(processProperty);

            currentTask.getProcess().setWikiField(prepareSolutionWikiField(currentTask.getProcess(), correctionTask));

            updateProcessSortHelperStatus(currentTask.getProcess());
        }
        return currentTask;
    }

    // TODO: find out if method should save or not task
    private void closeTasksBetweenCurrentAndCorrectionTask(Task currentTask, Task correctionTask) throws DataException {
        List<Task> allTasksInBetween = serviceManager.getTaskService().getAllTasksInBetween(
            correctionTask.getOrdering(), currentTask.getOrdering(), currentTask.getProcess().getId());
        for (Task taskInBetween : allTasksInBetween) {
            taskInBetween.setProcessingStatusEnum(TaskStatus.LOCKED);
            taskInBetween = serviceManager.getTaskService().setCorrectionStep(taskInBetween);
            taskInBetween.setProcessingEnd(null);
            serviceManager.getTaskService().save(taskInBetween);
        }
    }

    // TODO: shouldn't both methods be the same?! Why for batch is different than
    // for form?!
    private void closeTasksBetweenCurrentAndCorrectionTaskA(Task currentTask, Task correctionTask, Date date)
            throws DataException {
        List<Task> allTasksInBetween = serviceManager.getTaskService().getAllTasksInBetween(
            correctionTask.getOrdering(), currentTask.getOrdering(), currentTask.getProcess().getId());
        for (Task taskInBetween : allTasksInBetween) {
            taskInBetween.setProcessingStatusEnum(TaskStatus.DONE);
            taskInBetween.setProcessingEnd(date);
            taskInBetween.setPriority(0);

            // this two lines differs both methods
            currentTask.setProcessingTime(date);
            currentTask.setProcessingUser(this.user);
            // this two lines differs both methods

            serviceManager.getTaskService().save(prepareTaskForClose(currentTask, taskInBetween, date));
        }
    }

    private void closeTasksBetweenCurrentAndCorrectionTaskB(Task currentTask, Task correctionTask, Date date)
            throws DataException {
        List<Task> tasksInBetween = serviceManager.getTaskService().getAllTasksInBetween(correctionTask.getOrdering(),
            currentTask.getOrdering(), currentTask.getProcess().getId());
        for (Task taskInBetween : tasksInBetween) {
            taskInBetween.setProcessingStatusEnum(TaskStatus.DONE);
            taskInBetween.setProcessingEnd(date);
            taskInBetween.setPriority(0);
            this.serviceManager.getTaskService().save(prepareTaskForClose(currentTask, taskInBetween, date));
        }
    }

    private Task prepareTaskForClose(Task currentTask, Task taskInBetween, Date date) {
        if (taskInBetween.getId().intValue() == currentTask.getId().intValue()) {
            taskInBetween.setProcessingStatusEnum(TaskStatus.OPEN);
            taskInBetween = serviceManager.getTaskService().setCorrectionStep(taskInBetween);
            taskInBetween.setProcessingEnd(null);
            taskInBetween.setProcessingTime(date);
        }
        return taskInBetween;
    }

    private Property prepareProblemMessageProperty(Date date) {
        Property processProperty = new Property();
        processProperty.setTitle(Helper.getTranslation("Korrektur notwendig"));
        processProperty.setValue("[" + this.formatter.format(date) + ", "
                + serviceManager.getUserService().getFullName(this.user) + "] " + this.problem.getMessage());
        processProperty.setType(PropertyType.messageError);
        return processProperty;
    }

    private Property prepareSolveMessageProperty(Task correctionTask) {
        Property processProperty = new Property();
        processProperty.setTitle(Helper.getTranslation("Korrektur durchgefuehrt"));
        processProperty.setValue(
            "[" + this.formatter.format(new Date()) + ", " + serviceManager.getUserService().getFullName(this.user)
                    + "] " + Helper.getTranslation("KorrekturloesungFuer") + " " + correctionTask.getTitle() + ": "
                    + this.solution.getMessage());
        processProperty.setType(PropertyType.messageImportant);
        return processProperty;
    }

    private String prepareProblemWikiField(Process process, Task correctionTask) {
        String message = Helper.getTranslation("KorrekturFuer") + " " + correctionTask.getTitle() + ": "
                + this.problem.getMessage() + " (" + serviceManager.getUserService().getFullName(this.user) + ")";
        return WikiFieldHelper.getWikiMessage(process, process.getWikiField(), "error", message);
    }

    private String prepareSolutionWikiField(Process process, Task correctionTask) {
        String message = Helper.getTranslation("KorrekturloesungFuer") + " " + correctionTask.getTitle() + ": "
                + this.solution.getMessage() + " (" + serviceManager.getUserService().getFullName(this.user) + ")";
        return WikiFieldHelper.getWikiMessage(process, process.getWikiField(), "info", message);
    }

    private List<Task> getAllHigherTasks(List<Task> tasks, Task task) {
        List<Task> allHigherTasks = new ArrayList<>();
        this.openTasksWithTheSameOrdering = 0;
        for (Task tempTask : tasks) {
            if (tempTask.getOrdering().equals(task.getOrdering()) && tempTask.getProcessingStatus() != 3
                    && !tempTask.getId().equals(task.getId())) {
                openTasksWithTheSameOrdering++;
            } else if (tempTask.getOrdering() > task.getOrdering()) {
                allHigherTasks.add(tempTask);
            }
        }
        return allHigherTasks;
    }

    /**
     * If no open parallel tasks are available, activate the next tasks.
     */
    private void activateNextTask(List<Task> allHigherTasks) throws DataException {
        if (openTasksWithTheSameOrdering == 0) {
            int ordering = 0;
            boolean matched = false;
            for (Task task : allHigherTasks) {
                if (ordering < task.getOrdering() && !matched) {
                    ordering = task.getOrdering();
                }

                if (ordering == task.getOrdering() && task.getProcessingStatus() != 3
                        && task.getProcessingStatus() != 2) {
                    // activate the task if it is not fully automatic
                    task.setProcessingStatus(1);
                    task.setProcessingTime(new Date());
                    task.setEditType(4);

                    History historyOpen = new History(new Date(), task.getOrdering(), task.getTitle(),
                            HistoryTypeEnum.taskOpen, task.getProcess());
                    serviceManager.getHistoryService().save(historyOpen);

                    // if it is an automatic task with script
                    if (task.isTypeAutomatic()) {
                        automaticTasks.add(task);
                    } else if (task.isTypeAcceptClose()) {
                        tasksToFinish.add(task);
                    }

                    serviceManager.getTaskService().save(task);
                    matched = true;
                } else {
                    if (matched) {
                        break;
                    }
                }
            }
        }
    }

    /**
     * Update process sort helper status.
     *
     * @param process
     *            object
     */
    private void updateProcessSortHelperStatus(Process process) throws DataException {
        String value = serviceManager.getProcessService().getProgress(process, null);
        process.setSortHelperStatus(value);
        serviceManager.getProcessService().save(process);
    }

    /**
     * Download to user home directory.
     * 
     * @param task
     *            object
     */
    private void downloadToHome(Task task) {
        task.setProcessingTime(new Date());
        if (this.user != null) {
            task.setProcessingUser(this.user);
            this.webDav.downloadToHome(task.getProcess(), !task.isTypeImagesWrite());
        }
    }
}
