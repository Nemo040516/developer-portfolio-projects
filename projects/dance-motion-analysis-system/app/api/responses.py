from typing import Any

from flask import jsonify


def ok_response(data: Any = None, message: str = "ok", status: int = 200):
    payload = {
        "code": 0,
        "message": message,
        "data": {} if data is None else data,
    }
    return jsonify(payload), status


def error_response(code: int, message: str, data: Any = None, status: int = 400):
    payload = {
        "code": code,
        "message": message,
        "data": data,
    }
    return jsonify(payload), status
