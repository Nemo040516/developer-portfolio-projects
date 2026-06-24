import os

import pytest
from playwright.sync_api import expect


pytestmark = pytest.mark.e2e


@pytest.mark.skipif(
    not os.getenv("E2E_BASE_URL"),
    reason="Set E2E_BASE_URL to a running site before executing browser E2E tests.",
)
def test_homepage_primary_action(page):
    base_url = os.environ["E2E_BASE_URL"].rstrip("/")
    page.goto(base_url + "/")

    expect(page.get_by_role("link", name="开始分析")).to_be_visible()
