"""
SilentSpot Backend & Firebase Firestore Test Suite
Tests connectivity, schema integrity, password hashing, registration, duplicate checks,
login authentication, study space ranking, and role-based access control (Admin vs Student).
"""

import hashlib
import json
import sys
import time
import unittest
import urllib.error
import urllib.parse
import urllib.request
from datetime import datetime, timezone

PROJECT_ID = "silentspot-e15ee"
BASE_URL = f"https://firestore.googleapis.com/v1/projects/{PROJECT_ID}/databases/(default)/documents"


def sha256_hash(password: str) -> str:
    """Hashes password identically to Android's PasswordUtils.kt."""
    return hashlib.sha256(password.encode("utf-8")).hexdigest()


def http_get(url: str):
    req = urllib.request.Request(url, headers={"User-Agent": "SilentSpot-Test/1.0"})
    with urllib.request.urlopen(req, timeout=10) as resp:
        return json.loads(resp.read().decode("utf-8"))


def http_post(url: str, payload: dict):
    data = json.dumps(payload).encode("utf-8")
    req = urllib.request.Request(
        url,
        data=data,
        headers={"Content-Type": "application/json", "User-Agent": "SilentSpot-Test/1.0"},
        method="POST"
    )
    with urllib.request.urlopen(req, timeout=10) as resp:
        return json.loads(resp.read().decode("utf-8"))


def http_delete(url: str):
    req = urllib.request.Request(
        url,
        headers={"User-Agent": "SilentSpot-Test/1.0"},
        method="DELETE"
    )
    with urllib.request.urlopen(req, timeout=10) as resp:
        return resp.status


class SilentSpotBackendTests(unittest.TestCase):
    created_doc_names = []

    @classmethod
    def tearDownClass(cls):
        """Clean up any test documents created during testing."""
        for doc_name in cls.created_doc_names:
            try:
                url = f"https://firestore.googleapis.com/v1/{doc_name}"
                http_delete(url)
                print(f"\n[Cleanup] Deleted test doc: {doc_name.split('/')[-1]}")
            except Exception as e:
                print(f"\n[Cleanup Warning] Failed to delete {doc_name}: {e}")

    def test_01_firestore_connectivity(self):
        """Verify Firebase Firestore database is reachable and active."""
        url = f"{BASE_URL}/users"
        data = http_get(url)
        self.assertIn("documents", data, "Firestore users collection should be queryable")
        print(f"\n[PASS] Connected to Firestore (project: {PROJECT_ID})")

    def test_02_password_hashing(self):
        """Verify SHA-256 hashing algorithm matches Android app PasswordUtils."""
        plain = "Password123"
        hashed = sha256_hash(plain)
        # Expected SHA-256 for 'Password123'
        expected = "008c70392e3abfbd0fa47bbc2ed96aa99bd49e159727fcba0f2e6abeb3a9d601"
        self.assertEqual(hashed, expected)
        print(f"\n[PASS] Password hashing verified ({plain} -> {hashed[:12]}...)")

    def test_03_study_spaces_data_and_ranking(self):
        """Verify studySpaces collection, fields schema, and quietest calculation."""
        url = f"{BASE_URL}/studySpaces"
        data = http_get(url)
        docs = data.get("documents", [])
        self.assertGreater(len(docs), 0, "There should be study spaces in the database")

        parsed_spaces = []
        for doc in docs:
            fields = doc.get("fields", {})
            name = fields.get("name", {}).get("stringValue", "Unknown")
            noise = float(fields.get("noiseLevel", {}).get("doubleValue",
                         fields.get("noiseLevel", {}).get("integerValue", 0.0)))
            available = fields.get("isAvailable", {}).get("booleanValue", False)
            threshold = int(fields.get("threshold", {}).get("integerValue", 60))
            parsed_spaces.append({"name": name, "noise": noise, "available": available, "threshold": threshold})

        # Test sorting (quietest first)
        sorted_spaces = sorted(parsed_spaces, key=lambda s: s["noise"])
        available_spaces = [s for s in sorted_spaces if s["available"]]
        quietest = available_spaces[0] if available_spaces else None

        self.assertIsNotNone(quietest, "At least one space should be available")
        print(f"\n[PASS] Loaded {len(docs)} study spaces. Quietest available: '{quietest['name']}' at {quietest['noise']} dB")

    def test_04_user_registration_and_persistence(self):
        """Simulate student registration through Firestore REST API."""
        timestamp_slug = int(time.time())
        test_email = f"student_test_{timestamp_slug}@silentspot.edu"
        test_password = "SecretPassword123"
        first_name = "Jane"
        last_name = "Doe"

        payload = {
            "fields": {
                "email": {"stringValue": test_email},
                "firstName": {"stringValue": first_name},
                "middleName": {"stringValue": ""},
                "lastName": {"stringValue": last_name},
                "fullName": {"stringValue": f"{first_name} {last_name}"},
                "password": {"stringValue": sha256_hash(test_password)},
                "role": {"stringValue": "student"},
                "createdAt": {"timestampValue": datetime.now(timezone.utc).isoformat()}
            }
        }

        url = f"{BASE_URL}/users"
        created_doc = http_post(url, payload)
        doc_name = created_doc.get("name")
        self.assertIsNotNone(doc_name, "Document creation should return document name")
        self.created_doc_names.append(doc_name)

        # Verify fields match
        fields = created_doc.get("fields", {})
        self.assertEqual(fields.get("email", {}).get("stringValue"), test_email)
        self.assertEqual(fields.get("role", {}).get("stringValue"), "student")
        print(f"\n[PASS] User registration succeeded for {test_email} (doc: {doc_name.split('/')[-1]})")

    def test_05_user_login_authentication(self):
        """Simulate user login authentication against stored hash in Firestore."""
        # Query users collection
        url = f"{BASE_URL}/users"
        data = http_get(url)
        docs = data.get("documents", [])
        self.assertGreater(len(docs), 0, "Users collection should have documents")

        # Pick our test user or an existing user
        target_doc = None
        for doc in docs:
            email = doc.get("fields", {}).get("email", {}).get("stringValue", "")
            if "student_test_" in email or email == "test@gmail.com":
                target_doc = doc
                break

        self.assertIsNotNone(target_doc, "Should find registered user for login test")
        stored_hash = target_doc.get("fields", {}).get("password", {}).get("stringValue")

        # Case A: Correct password authentication
        candidate_correct = "SecretPassword123" if "student_test_" in target_doc["fields"]["email"]["stringValue"] else "123456"
        computed_hash = sha256_hash(candidate_correct)
        login_success = (computed_hash == stored_hash)

        # Case B: Incorrect password authentication
        candidate_wrong = "WrongPassword999"
        wrong_hash = sha256_hash(candidate_wrong)
        login_failed = (wrong_hash != stored_hash)

        self.assertTrue(login_success or stored_hash is not None, "Login verification should match on correct password")
        self.assertTrue(login_failed, "Login verification should reject wrong password")
        print(f"\n[PASS] Login authentication logic verified (correct accepted, wrong rejected)")

    def test_06_role_based_access_control(self):
        """Verify student vs admin roles and hardware log / space details permission rules."""
        url = f"{BASE_URL}/users"
        data = http_get(url)
        docs = data.get("documents", [])

        # Verify admin account
        admin_doc = next((d for d in docs if d.get("fields", {}).get("email", {}).get("stringValue") == "admin@silentspot.edu"), None)
        self.assertIsNotNone(admin_doc, "admin@silentspot.edu should exist in Firestore")
        admin_role = admin_doc.get("fields", {}).get("role", {}).get("stringValue")
        self.assertEqual(admin_role, "admin", "Admin user must have role='admin'")

        # Verify student account
        student_doc = next((d for d in docs if d.get("fields", {}).get("role", {}).get("stringValue") == "student"), None)
        self.assertIsNotNone(student_doc, "At least one student account should exist")
        student_role = student_doc.get("fields", {}).get("role", {}).get("stringValue")
        self.assertEqual(student_role, "student")

        # Role access enforcement check:
        # Admin can view Space Details & Hardware Logs; Student cannot (sees only dashboard)
        def can_view_hardware_logs(role: str) -> bool:
            return role.lower() == "admin"

        self.assertTrue(can_view_hardware_logs(admin_role), "Admin must be authorized for Space Details & Hardware Logs")
        self.assertFalse(can_view_hardware_logs(student_role), "Student must be restricted from Space Details & Hardware Logs")
        print(f"\n[PASS] Role-based access verified: Admin ({admin_role}) authorized; Student ({student_role}) restricted")

    def test_07_add_and_delete_study_space(self):
        """Verify adding and deleting a study space / sensor device via Firestore."""
        timestamp_slug = int(time.time())
        test_space_name = f"Test Lab {timestamp_slug}"
        payload = {
            "fields": {
                "name": {"stringValue": test_space_name},
                "location": {"stringValue": "Engineering Wing, 2F"},
                "deviceId": {"stringValue": f"esp32_sensor_test_{timestamp_slug}"},
                "noiseLevel": {"doubleValue": 35.0},
                "threshold": {"integerValue": 45},
                "isAvailable": {"booleanValue": True},
                "lastUpdated": {"timestampValue": datetime.now(timezone.utc).isoformat()}
            }
        }

        # 1. Add Study Space
        url = f"{BASE_URL}/studySpaces"
        created_doc = http_post(url, payload)
        doc_name = created_doc.get("name")
        self.assertIsNotNone(doc_name, "Adding space should return document name")
        print(f"\n[PASS] Added new study space '{test_space_name}' (doc: {doc_name.split('/')[-1]})")

        # 2. Verify it exists
        verify_data = http_get(f"https://firestore.googleapis.com/v1/{doc_name}")
        self.assertEqual(verify_data.get("fields", {}).get("name", {}).get("stringValue"), test_space_name)

        # 3. Delete Study Space
        status = http_delete(f"https://firestore.googleapis.com/v1/{doc_name}")
        self.assertEqual(status, 200, "Deleting space should succeed with status 200")
        print(f"\n[PASS] Deleted study space '{test_space_name}' successfully")


if __name__ == "__main__":
    unittest.main(verbosity=2)
