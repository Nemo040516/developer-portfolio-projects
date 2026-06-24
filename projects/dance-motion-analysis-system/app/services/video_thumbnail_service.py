from pathlib import Path

import cv2


THUMBNAIL_MAX_WIDTH = 480
THUMBNAIL_QUALITY = 88


def write_video_thumbnail(source_video_path: Path, output_image_path: Path) -> bool:
    capture = cv2.VideoCapture(str(source_video_path))
    try:
        if not capture.isOpened():
            return False

        frame_count = int(capture.get(cv2.CAP_PROP_FRAME_COUNT) or 0)
        candidate_indexes = []
        if frame_count > 0:
            candidate_indexes.append(max(0, min(frame_count - 1, int(frame_count * 0.2))))
            candidate_indexes.append(max(0, min(frame_count - 1, int(frame_count * 0.5))))
        candidate_indexes.append(0)

        frame = None
        for frame_index in dict.fromkeys(candidate_indexes):
            if frame_index > 0:
                capture.set(cv2.CAP_PROP_POS_FRAMES, float(frame_index))
            ok, next_frame = capture.read()
            if ok and next_frame is not None and next_frame.size > 0:
                frame = next_frame
                break

        if frame is None:
            return False

        height, width = frame.shape[:2]
        if width <= 0 or height <= 0:
            return False

        if width > THUMBNAIL_MAX_WIDTH:
            scale = THUMBNAIL_MAX_WIDTH / float(width)
            frame = cv2.resize(
                frame,
                (int(width * scale), int(height * scale)),
                interpolation=cv2.INTER_AREA,
            )

        output_image_path.parent.mkdir(parents=True, exist_ok=True)
        return bool(
            cv2.imwrite(
                str(output_image_path),
                frame,
                [int(cv2.IMWRITE_JPEG_QUALITY), THUMBNAIL_QUALITY],
            )
        )
    finally:
        capture.release()
