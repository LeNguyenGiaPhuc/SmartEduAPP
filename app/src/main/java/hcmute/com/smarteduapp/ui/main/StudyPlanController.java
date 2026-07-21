package hcmute.com.smarteduapp.ui.main;

import android.app.AlertDialog;
import android.view.Gravity;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.card.MaterialCardView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import hcmute.com.smarteduapp.R;
import hcmute.com.smarteduapp.data.local.model.StudyPlanListItem;
import hcmute.com.smarteduapp.data.repository.RepositoryCallback;
import hcmute.com.smarteduapp.ui.common.SimpleCardAdapter;
import hcmute.com.smarteduapp.ui.common.UiViewFactory;

/** Displays plans that were explicitly created from quiz analysis. */
class StudyPlanController {
    private final MainActivity activity;
    private final List<StudyPlanListItem> allPlans = new ArrayList<>();
    private long selectedSubjectId = -1L;
    private long selectedDocumentId = -1L;
    private long selectedAttemptId = -1L;

    StudyPlanController(MainActivity activity) {
        this.activity = activity;
    }

    void showPlanList() {
        activity.currentScreen = R.layout.screen_study_plan_list;
        activity.setContentView(R.layout.screen_study_plan_list);
        activity.applySystemBars();
        activity.bindClick(R.id.backFromStudyPlanList, activity::showHome);
        loadPlans();
    }

    private void loadPlans() {
        activity.studyRepository.getAllStudyPlans(new RepositoryCallback<List<StudyPlanListItem>>() {
            @Override
            public void onSuccess(List<StudyPlanListItem> plans) {
                allPlans.clear();
                if (plans != null) {
                    allPlans.addAll(plans);
                }
                setupFilters();
                renderPlans();
            }

            @Override
            public void onError(Exception exception) {
                Toast.makeText(activity, "Không thể tải kế hoạch học", Toast.LENGTH_SHORT).show();
                renderEmpty("Chưa có kế hoạch học nào.");
            }
        });
    }

    private void setupFilters() {
        Spinner subjectSpinner = activity.findViewById(R.id.spinnerPlanSubject);
        Spinner documentSpinner = activity.findViewById(R.id.spinnerPlanDocument);
        Spinner attemptSpinner = activity.findViewById(R.id.spinnerPlanAttempt);

        List<FilterOption> subjects = new ArrayList<>();
        subjects.add(new FilterOption(-1L, "Tất cả môn học"));
        Set<Long> subjectIds = new HashSet<>();
        for (StudyPlanListItem plan : allPlans) {
            if (subjectIds.add(plan.subjectId)) {
                subjects.add(new FilterOption(plan.subjectId, plan.subjectName));
            }
        }

        subjectSpinner.setAdapter(createSpinnerAdapter(subjects));
        subjectSpinner.setOnItemSelectedListener(new SimpleItemSelectedListener() {
            @Override
            public void onItemSelected(int position) {
                selectedSubjectId = subjects.get(position).id;
                selectedDocumentId = -1L;
                selectedAttemptId = -1L;
                setupDocumentFilter(documentSpinner, attemptSpinner);
                renderPlans();
            }
        });

        setupDocumentFilter(documentSpinner, attemptSpinner);
        attemptSpinner.setOnItemSelectedListener(new SimpleItemSelectedListener() {
            @Override
            public void onItemSelected(int position) {
                List<FilterOption> options = createAttemptOptions();
                selectedAttemptId = options.get(position).id;
                renderPlans();
            }
        });
    }

    private void setupDocumentFilter(Spinner documentSpinner, Spinner attemptSpinner) {
        List<FilterOption> documents = createDocumentOptions();
        documentSpinner.setAdapter(createSpinnerAdapter(documents));
        documentSpinner.setOnItemSelectedListener(new SimpleItemSelectedListener() {
            @Override
            public void onItemSelected(int position) {
                selectedDocumentId = documents.get(position).id;
                selectedAttemptId = -1L;
                List<FilterOption> attempts = createAttemptOptions();
                attemptSpinner.setAdapter(createSpinnerAdapter(attempts));
                renderPlans();
            }
        });
        List<FilterOption> attempts = createAttemptOptions();
        attemptSpinner.setAdapter(createSpinnerAdapter(attempts));
    }

    private List<FilterOption> createDocumentOptions() {
        List<FilterOption> options = new ArrayList<>();
        options.add(new FilterOption(-1L, "Tất cả tài liệu"));
        Set<Long> documentIds = new HashSet<>();
        for (StudyPlanListItem plan : allPlans) {
            if ((selectedSubjectId == -1L || plan.subjectId == selectedSubjectId)
                    && documentIds.add(plan.documentId)) {
                options.add(new FilterOption(plan.documentId, plan.documentTitle));
            }
        }
        return options;
    }

    private List<FilterOption> createAttemptOptions() {
        List<FilterOption> options = new ArrayList<>();
        options.add(new FilterOption(-1L, "Tất cả lần làm quiz"));
        SimpleDateFormat format = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault());
        Set<Long> attemptIds = new HashSet<>();
        for (StudyPlanListItem plan : allPlans) {
            if ((selectedSubjectId == -1L || plan.subjectId == selectedSubjectId)
                    && (selectedDocumentId == -1L || plan.documentId == selectedDocumentId)
                    && attemptIds.add(plan.attemptId)) {
                options.add(new FilterOption(
                        plan.attemptId,
                        "Lần " + format.format(new Date(plan.completedAt))
                                + " · " + String.format(Locale.US, "%.1f điểm", plan.score)
                ));
            }
        }
        return options;
    }

    private ArrayAdapter<String> createSpinnerAdapter(List<FilterOption> options) {
        List<String> labels = new ArrayList<>();
        for (FilterOption option : options) {
            labels.add(option.label);
        }
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                activity, android.R.layout.simple_spinner_item, labels
        );
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        return adapter;
    }

    private List<StudyPlanListItem> filteredPlans() {
        List<StudyPlanListItem> result = new ArrayList<>();
        for (StudyPlanListItem plan : allPlans) {
            if (selectedSubjectId != -1L && plan.subjectId != selectedSubjectId) continue;
            if (selectedDocumentId != -1L && plan.documentId != selectedDocumentId) continue;
            if (selectedAttemptId != -1L && plan.attemptId != selectedAttemptId) continue;
            result.add(plan);
        }
        return result;
    }

    private void renderPlans() {
        List<StudyPlanListItem> plans = filteredPlans();
        if (plans.isEmpty()) {
            renderEmpty(allPlans.isEmpty()
                    ? "Chưa có kế hoạch học nào. Hãy bấm Phân tích sau khi làm quiz."
                    : "Không có kế hoạch phù hợp với lựa chọn hiện tại.");
            return;
        }

        TextView empty = activity.findViewById(R.id.emptyStudyPlans);
        RecyclerView container = activity.findViewById(R.id.studyPlanListContainer);
        empty.setVisibility(View.GONE);
        container.setVisibility(View.VISIBLE);
        UiViewFactory.setupVerticalRecycler(container);

        SimpleCardAdapter adapter = new SimpleCardAdapter();
        List<SimpleCardAdapter.CardFactory> cards = new ArrayList<>();
        for (StudyPlanListItem plan : plans) {
            cards.add((parent, position) -> createPlanCard(plan, position));
        }
        container.setAdapter(adapter);
        adapter.submit(cards);
    }

    private void renderEmpty(String message) {
        TextView empty = activity.findViewById(R.id.emptyStudyPlans);
        RecyclerView container = activity.findViewById(R.id.studyPlanListContainer);
        if (empty == null || container == null) return;
        empty.setText(message);
        empty.setVisibility(View.VISIBLE);
        container.setVisibility(View.GONE);
    }

    private MaterialCardView createPlanCard(StudyPlanListItem plan, int index) {
        MaterialCardView card = UiViewFactory.createCard(activity);
        card.setOnClickListener(v -> activity.openStudyPlanFromList(plan.planId));

        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(UiViewFactory.dp(activity, 16), UiViewFactory.dp(activity, 14),
                UiViewFactory.dp(activity, 16), UiViewFactory.dp(activity, 12));
        content.addView(UiViewFactory.createText(activity, plan.documentTitle, 16, R.color.ink, true));
        content.addView(UiViewFactory.createText(activity,
                "Môn: " + plan.subjectName, 13, R.color.ink_muted, false));

        SimpleDateFormat format = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault());
        content.addView(UiViewFactory.createText(activity,
                plan.correctCount + "/" + (plan.correctCount + plan.wrongCount)
                        + " đúng · " + String.format(Locale.US, "%.1f điểm", plan.score)
                        + " · " + format.format(new Date(plan.completedAt)),
                13, R.color.brand_blue_dark, true));

        LinearLayout actions = new LinearLayout(activity);
        actions.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        actions.setPadding(0, UiViewFactory.dp(activity, 10), 0, 0);

        TextView open = UiViewFactory.createText(activity, "Mở kế hoạch", 13,
                R.color.brand_blue_dark, true);
        open.setGravity(Gravity.CENTER);
        open.setBackgroundResource(R.drawable.bg_badge_blue);
        open.setPadding(UiViewFactory.dp(activity, 16), UiViewFactory.dp(activity, 8),
                UiViewFactory.dp(activity, 16), UiViewFactory.dp(activity, 8));
        open.setOnClickListener(v -> activity.openStudyPlanFromList(plan.planId));

        TextView delete = UiViewFactory.createText(activity, "Xóa", 13, R.color.danger, true);
        delete.setGravity(Gravity.CENTER);
        delete.setBackgroundResource(R.drawable.bg_action_chip_danger);
        delete.setPadding(UiViewFactory.dp(activity, 18), UiViewFactory.dp(activity, 8),
                UiViewFactory.dp(activity, 18), UiViewFactory.dp(activity, 8));
        delete.setOnClickListener(v -> confirmDelete(plan));

        actions.addView(open);
        LinearLayout.LayoutParams deleteParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        deleteParams.setMargins(UiViewFactory.dp(activity, 8), 0, 0, 0);
        actions.addView(delete, deleteParams);
        content.addView(actions);
        card.addView(content);
        UiViewFactory.animateIn(card, index);
        return card;
    }

    private void confirmDelete(StudyPlanListItem plan) {
        new AlertDialog.Builder(activity)
                .setTitle("Xóa kế hoạch?")
                .setMessage("Kế hoạch của lần làm quiz này và các nhiệm vụ bên trong sẽ bị xóa.")
                .setNegativeButton("Hủy", null)
                .setPositiveButton("Xóa", (dialog, which) -> deletePlan(plan.planId))
                .show();
    }

    void deletePlan(long planId, Runnable onSuccess) {
        activity.studyRepository.deleteStudyPlan(planId, new RepositoryCallback<Integer>() {
            @Override
            public void onSuccess(Integer result) {
                onSuccess.run();
            }

            @Override
            public void onError(Exception exception) {
                Toast.makeText(activity, "Không thể xóa kế hoạch", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void deletePlan(long planId) {
        deletePlan(planId, this::loadPlans);
    }

    private static class FilterOption {
        final long id;
        final String label;

        FilterOption(long id, String label) {
            this.id = id;
            this.label = label == null ? "(Không có tên)" : label;
        }
    }

    private abstract static class SimpleItemSelectedListener
            implements android.widget.AdapterView.OnItemSelectedListener {
        public abstract void onItemSelected(int position);

        @Override
        public final void onItemSelected(android.widget.AdapterView<?> parent,
                                         View view, int position, long id) {
            onItemSelected(position);
        }

        @Override
        public void onNothingSelected(android.widget.AdapterView<?> parent) {
        }
    }
}
