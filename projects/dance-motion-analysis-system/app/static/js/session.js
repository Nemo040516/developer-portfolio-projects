(function bootstrapSessionPage(window, document) {
    "use strict";

    const api = window.MotionApiClient;

    function getCheckedSingleRole(elements) {
        const checkedInput = (elements.singleRoleInputs || []).find(function findChecked(input) {
            return input.checked;
        });
        return checkedInput ? checkedInput.value : "learner";
    }

    function buildDualRoleSummary(primaryRole, secondaryRole) {
        const primaryLabel = primaryRole === "standard" ? "视频 1 = 参考视频" : "视频 1 = 当前视频";
        const secondaryLabel = secondaryRole === "standard" ? "视频 2 = 参考视频" : "视频 2 = 当前视频";

        if (primaryRole === secondaryRole) {
            return "双视频时两个角色不能相同，请改成一条参考视频加一条当前视频。";
        }

        return primaryLabel + "，" + secondaryLabel + "。确认后会按这个角色分配上传，并直接进入双视频评分。";
    }

    function syncRoleState(elements) {
        const hasPrimaryFile = Boolean(elements.primaryInput.files && elements.primaryInput.files[0]);
        const hasSecondaryFile = Boolean(elements.secondaryInput.files && elements.secondaryInput.files[0]);
        const isDualMode = hasPrimaryFile && hasSecondaryFile;

        if (elements.singleRoleGroup) {
            elements.singleRoleGroup.hidden = isDualMode;
        }
        if (elements.dualRoleGroup) {
            elements.dualRoleGroup.hidden = !isDualMode;
        }
        if (elements.swapRolesButton) {
            elements.swapRolesButton.disabled = !isDualMode;
        }

        if (!hasPrimaryFile && !hasSecondaryFile) {
            if (elements.roleModeBadge) {
                elements.roleModeBadge.textContent = "等待视频";
            }
            if (elements.roleCopy) {
                elements.roleCopy.textContent = "当前会根据你上传的视频数量，切换成单视频确认或双视频角色确认。";
            }
            if (elements.roleSummary) {
                elements.roleSummary.innerHTML = "<p>当前默认规则：上传 1 个视频时按“当前视频”处理，上传 2 个视频时按“视频 1 参考 / 视频 2 当前”处理。</p>";
            }
            return;
        }

        if (isDualMode) {
            const primaryRole = elements.primaryRoleSelect ? elements.primaryRoleSelect.value : "standard";
            const secondaryRole = elements.secondaryRoleSelect ? elements.secondaryRoleSelect.value : "learner";

            if (elements.roleModeBadge) {
                elements.roleModeBadge.textContent = "双视频角色确认";
            }
            if (elements.roleCopy) {
                elements.roleCopy.textContent = "这一步只决定谁是参考视频、谁是当前视频。确认后，系统会按这个角色分配上传。";
            }
            if (elements.roleSummary) {
                elements.roleSummary.innerHTML = "<p>" + api.escapeHtml(buildDualRoleSummary(primaryRole, secondaryRole)) + "</p>";
            }
            return;
        }

        const singleRole = getCheckedSingleRole(elements);
        if (elements.roleModeBadge) {
            elements.roleModeBadge.textContent = singleRole === "standard" ? "单视频参考预览" : "单视频当前观察";
        }
        if (elements.roleCopy) {
            elements.roleCopy.textContent = singleRole === "standard"
                ? "这段视频会作为参考视频上传，并直接进入参考视频预览。"
                : "这段视频会作为当前视频上传，并直接进入单视频动作观察。";
        }
        if (elements.roleSummary) {
            elements.roleSummary.innerHTML = singleRole === "standard"
                ? "<p>当前会把这段视频当作参考视频处理，上传后直接进入参考视频预览页。</p>"
                : "<p>当前会把这段视频当作当前视频处理，上传后直接进入单视频观察模式。</p>";
        }
    }

    function setStatus(elements, statusText, feedbackText) {
        if (elements.status) {
            elements.status.textContent = statusText;
        }
        if (elements.feedback) {
            elements.feedback.innerHTML = "<p>" + api.escapeHtml(feedbackText) + "</p>";
        }
    }

    async function uploadVideo(file, videoType) {
        const formData = new FormData();
        formData.append("file", file);
        const endpoint = videoType === "standard" ? "/api/videos/standard" : "/api/videos/learner";
        const data = await api.postForm(endpoint, formData);
        return data.video;
    }

    async function handleSubmit(event, elements) {
        event.preventDefault();

        const primaryFile = elements.primaryInput.files && elements.primaryInput.files[0];
        const secondaryFile = elements.secondaryInput.files && elements.secondaryInput.files[0];

        if (!primaryFile && !secondaryFile) {
            setStatus(elements, "缺少视频", "请至少选择 1 个视频。");
            return;
        }

        try {
            if (primaryFile && secondaryFile) {
                const primaryRole = elements.primaryRoleSelect ? elements.primaryRoleSelect.value : "standard";
                const secondaryRole = elements.secondaryRoleSelect ? elements.secondaryRoleSelect.value : "learner";

                if (primaryRole === secondaryRole) {
                    setStatus(elements, "角色冲突", "双视频模式下，两个视频不能使用相同角色。");
                    syncRoleState(elements);
                    return;
                }

                const standardFile = primaryRole === "standard" ? primaryFile : secondaryFile;
                const learnerFile = primaryRole === "learner" ? primaryFile : secondaryFile;

                setStatus(elements, "上传中", "正在按已确认角色上传 2 个视频，并准备双视频评分模式。");
                const standardVideo = await uploadVideo(standardFile, "standard");
                const learnerVideo = await uploadVideo(learnerFile, "learner");
                window.location.href = "/analysis?standard_video_id="
                    + encodeURIComponent(standardVideo.id)
                    + "&learner_video_id="
                    + encodeURIComponent(learnerVideo.id)
                    + "&preview_target=both";
                return;
            }

            const singleFile = primaryFile || secondaryFile;
            const singleRole = getCheckedSingleRole(elements);

            if (singleRole === "standard") {
                setStatus(elements, "上传中", "正在上传视频，并准备参考视频预览。");
                const standardVideo = await uploadVideo(singleFile, "standard");
                window.location.href = "/standard-preview?video_id="
                    + encodeURIComponent(standardVideo.id);
                return;
            }

            setStatus(elements, "上传中", "正在上传视频，并准备单视频观察模式。");
            const learnerVideo = await uploadVideo(singleFile, "learner");
            window.location.href = "/analysis?learner_video_id="
                + encodeURIComponent(learnerVideo.id)
                + "&preview_target=learner";
        } catch (error) {
            setStatus(elements, "导入失败", "视频导入失败：" + error.message);
        }
    }

    function handleSwapRoles(elements) {
        if (!elements.primaryRoleSelect || !elements.secondaryRoleSelect) {
            return;
        }
        const nextPrimaryRole = elements.secondaryRoleSelect.value;
        elements.secondaryRoleSelect.value = elements.primaryRoleSelect.value;
        elements.primaryRoleSelect.value = nextPrimaryRole;
        syncRoleState(elements);
    }

    function init() {
        if (!api) {
            return;
        }

        const elements = {
            form: document.getElementById("session-form"),
            primaryInput: document.getElementById("session-video-primary"),
            secondaryInput: document.getElementById("session-video-secondary"),
            status: document.getElementById("session-status"),
            feedback: document.getElementById("session-feedback"),
            roleModeBadge: document.getElementById("session-role-mode"),
            roleCopy: document.getElementById("session-role-copy"),
            roleSummary: document.getElementById("session-role-summary"),
            singleRoleGroup: document.getElementById("session-single-role-group"),
            dualRoleGroup: document.getElementById("session-dual-role-group"),
            primaryRoleSelect: document.getElementById("session-primary-role"),
            secondaryRoleSelect: document.getElementById("session-secondary-role"),
            swapRolesButton: document.getElementById("session-swap-roles"),
            singleRoleInputs: Array.from(document.querySelectorAll("input[name='session-single-role']")),
        };

        if (!elements.form || !elements.primaryInput || !elements.secondaryInput || !elements.status || !elements.feedback) {
            return;
        }

        [elements.primaryInput, elements.secondaryInput].forEach(function bindFileInput(input) {
            input.addEventListener("change", function onFileChange() {
                syncRoleState(elements);
            });
        });

        [elements.primaryRoleSelect, elements.secondaryRoleSelect].forEach(function bindRoleSelect(select) {
            if (!select) {
                return;
            }
            select.addEventListener("change", function onRoleChange() {
                syncRoleState(elements);
            });
        });

        elements.singleRoleInputs.forEach(function bindSingleRole(input) {
            input.addEventListener("change", function onSingleRoleChange() {
                syncRoleState(elements);
            });
        });

        if (elements.swapRolesButton) {
            elements.swapRolesButton.addEventListener("click", function onSwapRoles() {
                handleSwapRoles(elements);
            });
        }

        syncRoleState(elements);

        elements.form.addEventListener("submit", function onSubmit(event) {
            handleSubmit(event, elements);
        });
    }

    document.addEventListener("DOMContentLoaded", init);
})(window, document);
