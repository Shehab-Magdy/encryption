import sys
import json
import os
from datetime import datetime
from PyQt5.QtWidgets import QApplication, QWidget, QLabel, QLineEdit, QPushButton, QVBoxLayout, QTextEdit, QMessageBox
from PyQt5.QtCore import Qt
from PyQt5.QtGui import QFont
from license_core import LicenseManager

class AdminApp(QWidget):
    def __init__(self):
        super().__init__()
        self.lm = LicenseManager()
        self.db_file = os.path.join(os.path.dirname(sys.executable), 'licenses_db.json')
        self.initUI()

    def initUI(self):
        self.setWindowTitle('License Generator')
        self.setGeometry(300, 300, 500, 300)

        self.layout = QVBoxLayout()

        self.input_label = QLabel('أدخل الكود المشفر (Method A):')
        self.layout.addWidget(self.input_label)

        self.input_text = QTextEdit()
        self.input_text.setMaximumHeight(50)
        self.layout.addWidget(self.input_text)

        self.generate_button = QPushButton('إنشاء كود التفعيل')
        self.generate_button.clicked.connect(self.generate)
        self.layout.addWidget(self.generate_button)

        self.output_label = QLabel('كود التفعيل:')
        self.layout.addWidget(self.output_label)

        self.output_text = QTextEdit()
        self.output_text.setReadOnly(True)
        self.layout.addWidget(self.output_text)

        self.copy_activation_button = QPushButton('نسخ كود التفعيل')
        self.copy_activation_button.clicked.connect(self.copy_activation)
        self.copy_activation_button.hide()
        self.layout.addWidget(self.copy_activation_button)

        self.setLayout(self.layout)

    def generate(self):
        encrypted_hid = self.input_text.toPlainText().strip()
        if not encrypted_hid:
            QMessageBox.warning(self, 'خطأ', 'يرجى إدخال الكود المشفر')
            return
        try:
            hid = self.lm.decrypt_A(encrypted_hid)
            activation_code = self.lm.encrypt_B(hid)
            self.output_text.setText(activation_code)
            self.save_to_db(hid, encrypted_hid, activation_code)
            self.copy_activation_button.show()
            QMessageBox.information(self, 'نجح', 'تم إنشاء كود التفعيل وحفظه في قاعدة البيانات')
        except Exception as e:
            QMessageBox.warning(self, 'خطأ', f'فشل في فك التشفير: {str(e)}')

    def save_to_db(self, hid, hid_a, hid_b):
        db = self.load_db()
        record = {
            'ID': len(db) + 1,
            'HID_raw': hid,
            'HID_A': hid_a,
            'HID_B': hid_b,
            'created_at': datetime.now().isoformat()
        }
        db.append(record)
        with open(self.db_file, 'w', encoding='utf-8') as f:
            json.dump(db, f, indent=2, ensure_ascii=False)

    def load_db(self):
        if not os.path.exists(self.db_file):
            return []
        try:
            with open(self.db_file, 'r', encoding='utf-8') as f:
                return json.load(f)
        except:
            return []

    def copy_activation(self):
        text = self.output_text.toPlainText()
        if text:
            QApplication.clipboard().setText(text)
            QMessageBox.information(self, 'نجح', 'تم نسخ كود التفعيل إلى الحافظة')

if __name__ == '__main__':
    app = QApplication(sys.argv)
    ex = AdminApp()
    ex.show()
    sys.exit(app.exec_())