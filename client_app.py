import sys
import os
from PyQt5.QtWidgets import QApplication, QWidget, QLabel, QLineEdit, QPushButton, QVBoxLayout, QMessageBox
from PyQt5.QtCore import Qt
from PyQt5.QtGui import QFont
from license_core import LicenseManager

class LicenseApp(QWidget):
    def __init__(self):
        super().__init__()
        license_path = os.path.join(os.path.dirname(sys.executable), 'license.json')
        self.lm = LicenseManager(license_file=license_path)
        self.initUI()
        self.check_license()

    def initUI(self):
        self.setWindowTitle('License Validator')
        self.setGeometry(300, 300, 400, 200)

        self.layout = QVBoxLayout()

        self.status_label = QLabel('')
        self.status_label.setAlignment(Qt.AlignCenter)
        self.status_label.setFont(QFont('Arial', 12))
        self.layout.addWidget(self.status_label)

        self.hid_label = QLabel('')
        self.hid_label.setAlignment(Qt.AlignCenter)
        self.hid_label.setFont(QFont('Arial', 10))
        self.layout.addWidget(self.hid_label)

        self.copy_hid_button = QPushButton('نسخ')
        self.copy_hid_button.clicked.connect(self.copy_hid)
        self.copy_hid_button.hide()
        self.layout.addWidget(self.copy_hid_button)

        self.activation_input = QLineEdit()
        self.activation_input.setPlaceholderText('أدخل كود التفعيل')
        self.layout.addWidget(self.activation_input)

        self.activate_button = QPushButton('تفعيل')
        self.activate_button.clicked.connect(self.activate)
        self.layout.addWidget(self.activate_button)

        self.setLayout(self.layout)

    def check_license(self):
        if self.lm.validate_license():
            self.status_label.setText('الترخيص صالح')
            self.status_label.setStyleSheet('color: green')
            self.hid_label.setText('')
            self.activation_input.hide()
            self.activate_button.hide()
            self.copy_hid_button.hide()
        else:
            hid = self.lm.get_hardware_id()
            encrypted_hid = self.lm.encrypt_A(hid)
            self.status_label.setText('برجاء ارسال تلك الاحرف الى الرقم التالي: ٠١٠٠٩٣٥٢٨٢٣')
            self.status_label.setStyleSheet('color: red')
            self.hid_label.setText(encrypted_hid)
            self.activation_input.show()
            self.activate_button.show()
            self.copy_hid_button.show()

    def activate(self):
        code = self.activation_input.text().strip()
        if not code:
            QMessageBox.warning(self, 'خطأ', 'يرجى إدخال كود التفعيل')
            return
        if self.lm.activate_license(code):
            QMessageBox.information(self, 'نجح', 'تم تفعيل الترخيص بنجاح')
            self.check_license()
        else:
            QMessageBox.warning(self, 'فشل', 'كود التفعيل غير صحيح أو هذا جهاز جديد')

    def copy_hid(self):
        text = self.hid_label.text()
        if text:
            QApplication.clipboard().setText(text)
            QMessageBox.information(self, 'نجح', 'تم نسخ الكود إلى الحافظة')

if __name__ == '__main__':
    app = QApplication(sys.argv)
    ex = LicenseApp()
    ex.show()
    sys.exit(app.exec_())