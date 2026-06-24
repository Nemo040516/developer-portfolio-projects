from flask import Blueprint, current_app, redirect, render_template, request, url_for


web_bp = Blueprint("web", __name__)


PAGE_SHELL_CONTEXT = {
    "home": {
        "page_stage": "动作分析入口",
        "page_focus_title": "本页只做什么",
        "page_focus_points": [
            "从这里直接开始一次新分析。",
            "上传 1 个视频可进入单视频观察。",
            "上传 2 个视频可进入双视频评分。",
        ],
        "page_tags": ["轻量分析工具", "本地上传", "结果可保存"],
        "page_shell_variant": "compact",
        "show_page_guide": False,
        "show_page_tags": False,
        "show_page_hero": False,
        "show_nav_hints": False,
        "show_header_meta": False,
        "shell_density": "compact",
    },
    "session": {
        "page_stage": "导入视频",
        "page_focus_title": "推荐方式",
        "page_focus_points": [
            "上传 1 个视频，进入单视频观察模式。",
            "上传 2 个视频，进入双视频评分模式。",
            "当前未保存前都视为临时分析。",
        ],
        "page_tags": ["单视频观察", "双视频评分", "临时态优先"],
        "page_shell_variant": "compact",
        "show_page_guide": False,
        "show_page_tags": False,
        "show_page_hero": False,
        "show_nav_hints": False,
        "show_header_meta": False,
        "shell_density": "compact",
    },
    "standard_videos": {
        "page_stage": "准备示范动作",
        "page_focus_title": "在这里先做什么",
        "page_focus_points": [
            "选一段动作清楚、角度稳定的示范视频。",
            "如果列表里没有合适的视频，再补充上传。",
            "确认后直接进入预览或开始比对。",
        ],
        "page_tags": ["选择示范动作", "支持补充上传", "可继续进入比对"],
        "page_shell_variant": "compact",
        "show_page_guide": False,
        "show_page_tags": False,
        "show_page_hero": False,
        "show_nav_hints": False,
        "show_header_meta": False,
        "shell_density": "compact",
    },
    "standard_preview": {
        "page_stage": "确认示范动作",
        "page_focus_title": "建议重点看",
        "page_focus_points": [
            "动作是否选对，是否适合拿来模仿。",
            "原视频和火柴人回放是否能对上同一动作节点。",
            "骨骼回放是否稳定，关键部位是否明显跳动。",
            "确认没问题后，再进入动作比对页。",
        ],
        "page_tags": ["原视频同步预览", "起止动作查看", "继续开始比对"],
        "page_shell_variant": "compact",
        "show_page_guide": False,
        "show_page_tags": False,
        "show_page_hero": False,
        "show_nav_hints": False,
        "show_header_meta": False,
        "shell_density": "compact",
    },
    "learner_preview": {
        "page_stage": "单视频观察",
        "page_focus_title": "建议重点看",
        "page_focus_points": [
            "练习视频是否上传正确。",
            "原视频和火柴人回放是否能对上同一动作节点。",
            "自己的动作骨骼是否稳定，能否反映真实趋势。",
            "确认后回到比对页生成结果。",
        ],
        "page_tags": ["单视频预览", "动作趋势查看", "关键点观察"],
        "page_shell_variant": "compact",
        "show_page_guide": False,
        "show_page_tags": False,
        "show_page_hero": False,
        "show_nav_hints": False,
        "show_header_meta": False,
        "shell_density": "compact",
    },
    "analysis": {
        "page_stage": "核心分析页",
        "page_focus_title": "本页完成的核心动作",
        "page_focus_points": [
            "单视频模式下，直接观察关键点和骨骼。",
            "双视频模式下，在这里确认对比并开始计算。",
            "结果生成后，进入独立结果页保存结果。",
        ],
        "page_tags": ["动作预览", "双视频对比", "开始计算"],
        "page_shell_variant": "compact",
        "show_page_guide": False,
        "show_page_tags": False,
        "show_page_hero": False,
        "show_nav_hints": False,
        "show_header_meta": False,
        "shell_density": "compact",
    },
    "history": {
        "page_stage": "查看已保存结果",
        "page_focus_title": "适合用来做什么",
        "page_focus_points": [
            "先按时间、关键词或结果状态筛选记录。",
            "这里只保留已保存的最终结果。",
            "需要新分析时，回到导入页重新开始。",
        ],
        "page_tags": ["搜索筛选", "结果摘要", "结果留痕"],
        "page_shell_variant": "compact",
        "show_page_guide": False,
        "show_page_tags": False,
        "show_page_hero": False,
        "show_nav_hints": False,
        "show_header_meta": False,
        "shell_density": "compact",
    },
}


def _build_shell_context(active_nav: str) -> dict:
    context = PAGE_SHELL_CONTEXT.get(active_nav, PAGE_SHELL_CONTEXT["home"])
    return {
        "page_stage": context["page_stage"],
        "page_focus_title": context["page_focus_title"],
        "page_focus_points": list(context["page_focus_points"]),
        "page_tags": list(context["page_tags"]),
        "page_shell_variant": context.get("page_shell_variant", "default"),
        "show_page_hero": context.get("show_page_hero", True),
        "show_page_guide": context.get("show_page_guide", True),
        "show_page_tags": context.get("show_page_tags", True),
        "show_nav_hints": context.get("show_nav_hints", False),
        "show_header_meta": context.get("show_header_meta", False),
        "shell_density": context.get("shell_density", "compact"),
    }


def _render_page(
    template_name: str,
    page_title: str,
    active_nav: str,
    page_summary: str,
    **context,
):
    shell_context = _build_shell_context(active_nav)
    shell_context.update(context)
    return render_template(
        template_name,
        app_name=current_app.config["APP_NAME"],
        product_name=current_app.config["PRODUCT_NAME"],
        product_tagline=current_app.config["PRODUCT_TAGLINE"],
        page_title=page_title,
        active_nav=active_nav,
        page_summary=page_summary,
        **shell_context,
    )


def _build_preview_page_context(kind: str) -> dict:
    if kind == "learner":
        return {
            "preview_scope_title": "先看自己的练习动作",
            "preview_scope_description": "开始比对前，先确认这段练习视频是不是本次要看的内容，并在同一页对照原视频和火柴人回放是否同步、动作走势是否真实。",
            "preview_scope_tip": "建议先看 3 件事：视频是否选对、原视频与火柴人是否同步、骨骼是否稳定。",
            "preview_search_label": "筛选练习视频",
            "preview_search_placeholder": "按文件名、编号或时间筛选",
            "preview_select_label": "选择练习视频",
            "preview_picker_title": "可用练习视频",
            "preview_picker_description": "下方会展示匹配当前筛选条件的练习视频，你可以先选中，再开始加载同步预览。",
            "preview_selected_placeholder": "选中一条练习视频后，这里会先显示摘要，再决定要不要开始加载预览。",
            "preview_load_button_text": "查看练习预览",
            "preview_meta_placeholder": "加载后，这里会显示练习视频摘要、建议下一步和可展开的处理信息。",
            "preview_feedback_placeholder": "当前还没有打开练习预览。你可以先在比对页上传练习视频，再返回这里查看。",
            "preview_stage_title": "练习动作同步预览",
            "preview_stage_description": "左侧原视频和右侧火柴人会共用一套播放控制，方便你先对照自己的动作走势，再决定是否立即开始本次比对。",
            "preview_enable_3d": False,
            "preview_angle_list_title": "重点关节",
            "preview_trajectory_list_title": "重点轨迹",
            "preview_js_config": {
                "pagePath": "/learner-preview",
                "apiPathPrefix": "/api/videos/learner/",
                "listApiPath": "/api/videos/learner",
                "videoTypeLabel": "练习视频",
                "invalidIdText": "请先选择一条有效的练习视频。",
                "loadingText": "正在读取练习视频预览结果。",
                "successStatusText": "已完成",
                "successFeedbackLines": [
                    "练习视频同步预览已就绪。",
                    "现在可以同页对照原视频和火柴人，看自己的动作走势是否清楚。",
                    "如果骨骼明显跳动或姿态异常，优先说明当前视频质量或姿态提取结果还需要再确认。"
                ],
                "errorPrefix": "练习视频预览加载失败：",
                "metaEmptyText": "当前无法展示练习视频预览。",
                "selectionEmptyText": "当前还没有可用的练习视频。你可以先回到比对页上传，再回来查看。",
                "filterEmptyText": "当前筛选条件下没有匹配的练习视频。",
                "nextStepText": "如果原视频和火柴人同步正常、动作走势也清楚，就回到比对页继续生成本次练习结果。",
                "actionLinks": [
                    {"label": "回到比对页", "hrefTemplate": "/analysis?learner_video_id={id}"},
                    {"label": "回到首页", "hrefTemplate": "/"}
                ],
                "playerAriaLabel": "练习视频骨骼预览",
            },
        }

    return {
        "preview_scope_title": "先确认示范动作是不是你要练的",
        "preview_scope_description": "开始比对前，先在同一页对照这段示范视频的原视频和火柴人回放，确认它们同步正常、动作清楚，适合作为本次练习参考。",
        "preview_scope_tip": "建议先看 3 件事：动作是否选对、原视频与火柴人是否同步、关键部位有没有明显错位。",
        "preview_search_label": "筛选示范视频",
        "preview_search_placeholder": "按文件名、编号或时间筛选",
        "preview_select_label": "选择示范视频",
        "preview_picker_title": "可用示范视频",
        "preview_picker_description": "下方会展示匹配当前筛选条件的示范视频，你可以先选中，再开始加载同步预览。",
        "preview_selected_placeholder": "选中一条示范视频后，这里会先显示摘要，再决定要不要开始加载预览。",
        "preview_load_button_text": "查看示范预览",
        "preview_meta_placeholder": "加载后，这里会显示示范视频摘要、建议下一步和可展开的处理信息。",
        "preview_feedback_placeholder": "当前还没有打开示范预览。你可以从示范视频页进入，也可以直接在这里筛选并选择一条示范视频。",
        "preview_stage_title": "示范动作同步预览",
        "preview_stage_description": "左侧原视频和右侧火柴人会共用一套播放控制，帮助你先确认这段示范动作是否清楚、稳定，适不适合作为对照参考。",
        "preview_enable_3d": True,
        "preview_3d_stage_title": "三维骨架观察",
        "preview_3d_stage_description": "拖拽可旋转视角，滚轮可缩放。这里使用的是单目姿态估计给出的相对深度，只用于辅助观察空间关系。",
        "preview_angle_list_title": "重点关节",
        "preview_trajectory_list_title": "重点轨迹",
        "preview_js_config": {
            "pagePath": "/standard-preview",
            "apiPathPrefix": "/api/videos/standard/",
            "listApiPath": "/api/videos/standard",
            "enable3dPreview": True,
            "videoTypeLabel": "示范视频",
            "invalidIdText": "请先选择一条有效的示范视频。",
            "loadingText": "正在读取示范视频预览结果。",
            "successStatusText": "已完成",
            "successFeedbackLines": [
                "示范视频同步预览已就绪。",
                "现在可以同页对照原视频和火柴人，确认动作清楚、同步正常后再进入动作比对。",
                "如果这里已经出现明显异常，建议先换一段示范视频，再继续后面的练习流程。"
            ],
            "errorPrefix": "示范视频预览加载失败：",
            "metaEmptyText": "当前无法展示示范视频预览。",
            "selectionEmptyText": "当前还没有可用的示范视频。请先回到示范视频页补充上传。",
            "filterEmptyText": "当前筛选条件下没有匹配的示范视频。",
            "nextStepText": "确认原视频和火柴人同步正常、示范动作适合继续参考后，再进入比对页上传练习视频。",
            "actionLinks": [
                {"label": "回到示范视频页", "hrefTemplate": "/standard-videos?video_id={id}"},
                {"label": "用这段动作开始比对", "hrefTemplate": "/analysis?standard_video_id={id}"}
            ],
            "playerAriaLabel": "示范视频骨骼预览",
        },
    }


@web_bp.get("/")
def index():
    return _render_page(
        "index.html",
        "动作分析中心",
        "home",
        "这里不做推荐和恢复，只负责开始一次新分析，或查看已经保存的结果。",
    )


@web_bp.get("/session")
def session():
    return _render_page(
        "session.html",
        "视频导入与任务配置",
        "session",
        "上传 1 个视频进入单视频观察，上传 2 个视频进入双视频评分。未保存前都视为临时分析。",
    )


@web_bp.get("/standard-videos")
def standard_videos():
    return _render_page(
        "standard_videos.html",
        "参考视频素材",
        "standard_videos",
        "这里保留可复用的参考视频素材。当前主流程更推荐直接从导入页上传开始。",
    )


@web_bp.get("/standard-preview")
def standard_preview():
    return _render_page(
        "standard_preview.html",
        "查看示范预览",
        "standard_preview",
        "在开始比对前，先同页看看这段示范动作的原视频和火柴人是否同步、动作是否清楚，再决定是否把它作为本次练习参考。",
        **_build_preview_page_context("standard"),
    )


@web_bp.get("/learner-preview")
def learner_preview():
    return _render_page(
        "learner_preview.html",
        "单视频动作预览",
        "learner_preview",
        "当你只上传 1 个视频时，可先在这里查看原视频和骨骼回放，直观判断动作效果。",
        **_build_preview_page_context("learner"),
    )


@web_bp.get("/analysis")
def analysis():
    analysis_id = request.args.get("analysis_id", type=int)
    if analysis_id and analysis_id > 0:
        return redirect(url_for("web.review", analysis_id=analysis_id))

    return _render_page(
        "analysis.html",
        "动作预览与分析",
        "analysis",
        "这里是核心分析页：可承接单视频观察，也可承接双视频对比和结果计算。",
    )


@web_bp.get("/practice-stage")
def practice_stage():
    target = url_for("web.analysis")
    query = request.query_string.decode("utf-8").strip()
    if query:
        target = target + "?" + query
    return redirect(target)


@web_bp.get("/review")
def review():
    return _render_page(
        "review.html",
        "分析结果报告",
        "history",
        "这里专门用来查看一次分析结果：看回放、看摘要、看建议，并决定是否保存结果。",
        page_stage="查看单次结果",
        page_focus_title="本页只做什么",
        page_focus_points=[
            "先看这次分析用了哪组视频。",
            "再结合回放、总分和建议判断问题出在哪个片段。",
            "最后决定是保存结果，还是重新开始新分析。",
        ],
        page_tags=["分析结果", "双视频回看", "保存建议"],
    )


@web_bp.get("/history")
def history():
    return _render_page(
        "history.html",
        "结果记录",
        "history",
        "这里保留已经保存的分析结果，方便你回看摘要、评分和建议。",
    )
