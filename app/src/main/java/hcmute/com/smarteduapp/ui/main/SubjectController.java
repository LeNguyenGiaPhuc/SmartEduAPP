package hcmute.com.smarteduapp.ui.main;

import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

import hcmute.com.smarteduapp.R;
import hcmute.com.smarteduapp.data.local.entity.StudyDocument;
import hcmute.com.smarteduapp.data.local.entity.Subject;
import hcmute.com.smarteduapp.data.repository.RepositoryCallback;

/**
 * Owns subject CRUD and the subject-detail document list.
 */
class SubjectController {
    private final MainActivity activity;

    SubjectController(MainActivity activity) {
        this.activity = activity;
    }

    void showSubjectList() {
        activity.currentScreen = R.layout.screen_subject_list;
        activity.setContentView(R.layout.screen_subject_list);
        activity.applySystemBars();

        activity.bindClick(R.id.backHomeFromSubjectList, activity::showHome);
        activity.bindClick(R.id.buttonAddSubject, () -> activity.showSubjectForm(-1L));
        loadSubjects();
    }

    private void loadSubjects() {
        activity.subjectRepository.getAll(new RepositoryCallback<List<Subject>>() {
            @Override
            public void onSuccess(List<Subject> subjects) {
                renderSubjects(subjects);
            }
        });
    }

    private void renderSubjects(List<Subject> subjects) {
        activity.syncRecentSubjects(subjects);
        activity.renderRecentSubjects();
        activity.subjectListRenderer.renderSubjects(subjects, subject -> {
            activity.selectedSubjectId = subject.id;
            activity.selectedSubject = subject;
            activity.rememberRecentSubject(subject);
            activity.showSubjectDetail();
        });
    }

    void showSubjectDetail() {
        if (activity.selectedSubjectId < 0) {
            activity.showHome();
            return;
        }
        activity.currentScreen = R.layout.screen_subject_detail;
        activity.setContentView(R.layout.screen_subject_detail);
        activity.applySystemBars();

        activity.findViewById(R.id.cardActivityDocument).setVisibility(View.GONE);
        activity.findViewById(R.id.cardPendingDocument).setVisibility(View.GONE);
        activity.bindClick(R.id.backHomeFromSubject, activity::showHome);
        activity.bindClick(R.id.buttonEditSubject, () -> activity.showSubjectForm(activity.selectedSubjectId));
        activity.bindClick(R.id.buttonAddDocument, activity::showDocumentForm);
        loadSubjectDetail();
    }

    private void loadSubjectDetail() {
        activity.subjectRepository.getById(activity.selectedSubjectId, new RepositoryCallback<Subject>() {
            @Override
            public void onSuccess(Subject subject) {
                if (subject == null) {
                    activity.selectedSubjectId = -1L;
                    activity.selectedSubject = null;
                    activity.showHome();
                    return;
                }
                activity.selectedSubject = subject;
                activity.rememberRecentSubject(subject);
                activity.documentRepository.getBySubjectId(activity.selectedSubjectId,
                        new RepositoryCallback<List<StudyDocument>>() {
                            @Override
                            public void onSuccess(List<StudyDocument> documents) {
                                ((TextView) activity.findViewById(R.id.subjectTitle)).setText(subject.name);
                                renderDocuments(documents);
                            }
                        });
            }
        });
    }

    private void renderDocuments(List<StudyDocument> documents) {
        activity.subjectListRenderer.renderDocuments(documents, document -> activity.openDocument(document.id));
    }

    void showSubjectForm(long subjectId) {
        activity.currentScreen = R.layout.screen_subject_form;
        activity.setContentView(R.layout.screen_subject_form);
        activity.applySystemBars();

        EditText nameInput = activity.findViewById(R.id.inputSubjectName);
        EditText descriptionInput = activity.findViewById(R.id.inputSubjectDescription);
        TextView title = activity.findViewById(R.id.subjectFormTitle);
        View deleteButton = activity.findViewById(R.id.buttonDeleteSubject);
        boolean isEditing = subjectId > 0;
        deleteButton.setVisibility(isEditing ? View.VISIBLE : View.GONE);
        title.setText(isEditing ? "Chỉnh sửa môn học" : "Thêm môn học");

        if (isEditing) {
            activity.subjectRepository.getById(subjectId, new RepositoryCallback<Subject>() {
                @Override
                public void onSuccess(Subject subject) {
                    if (subject == null) {
                        activity.showHome();
                        return;
                    }
                    nameInput.setText(subject.name);
                    descriptionInput.setText(subject.description);
                }
            });
        }

        activity.bindClick(R.id.backSubjects, activity::showHome);
        activity.bindClick(R.id.buttonSaveSubject, () -> saveSubject(subjectId, nameInput, descriptionInput));
        activity.bindClick(R.id.buttonDeleteSubject, () -> deleteSubject(subjectId));
    }

    private void saveSubject(long subjectId, EditText nameInput, EditText descriptionInput) {
        String name = nameInput.getText().toString().trim();
        String description = descriptionInput.getText().toString().trim();
        if (name.isEmpty()) {
            nameInput.setError("Nhập tên môn học");
            return;
        }

        if (subjectId > 0) {
            activity.subjectRepository.getById(subjectId, new RepositoryCallback<Subject>() {
                @Override
                public void onSuccess(Subject subject) {
                    if (subject != null) {
                        subject.name = name;
                        subject.description = description;
                        activity.subjectRepository.update(subject, new RepositoryCallback<Integer>() {
                            @Override
                            public void onSuccess(Integer result) {
                                activity.selectedSubjectId = subjectId;
                                activity.selectedSubject = subject;
                                activity.showSubjectDetail();
                            }

                            @Override
                            public void onError(Exception exception) {
                                showSubjectSaveError();
                            }
                        });
                    }
                }
            });
            return;
        }

        activity.subjectRepository.create(name, description, new RepositoryCallback<Long>() {
            @Override
            public void onSuccess(Long id) {
                activity.selectedSubjectId = id;
                activity.showSubjectDetail();
            }

            @Override
            public void onError(Exception exception) {
                showSubjectSaveError();
            }
        });
    }

    private void deleteSubject(long subjectId) {
        if (subjectId <= 0) return;
        activity.subjectRepository.getById(subjectId, new RepositoryCallback<Subject>() {
            @Override
            public void onSuccess(Subject subject) {
                if (subject == null) return;
                activity.subjectRepository.delete(subject, new RepositoryCallback<Integer>() {
                    @Override
                    public void onSuccess(Integer result) {
                        activity.selectedSubjectId = -1L;
                        activity.selectedSubject = null;
                        activity.showHome();
                    }
                });
            }
        });
    }

    private void showSubjectSaveError() {
        Toast.makeText(activity, "Tên môn học đã tồn tại hoặc không thể lưu",
                Toast.LENGTH_SHORT).show();
    }
}
