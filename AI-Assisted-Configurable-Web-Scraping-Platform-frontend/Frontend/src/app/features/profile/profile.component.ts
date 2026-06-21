import {Component, inject, OnInit, signal} from '@angular/core';
import {CommonModule} from '@angular/common';
import {FormsModule} from '@angular/forms';
import {UserService} from '../../core/services/user.service';
import {ToastService} from '../../core/services/toast.service';


@Component({
  selector: 'app-profile',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './profile.component.html',
  styleUrl: './profile.component.scss',
})
export class ProfileComponent implements OnInit {

  private userService = inject(UserService);

  profile = this.userService.profile;
  loading = this.userService.loading;


  currentPassword = '';
  newPassword = '';
  confirmPassword = '';

  showCurrent = signal(false);
  showNew = signal(false);
  showConfirm = signal(false);

  pwdLoading = signal(false);
  pwdSuccess = signal('');
  pwdError = signal('');

  ngOnInit() {
    this.userService.getMyProfile().subscribe();
  }

  get initials(): string {
    return (this.profile()?.username ?? 'U').charAt(0).toUpperCase();
  }

  get memberSince(): string {
    const d = this.profile()?.createdAt;
    if (!d) return '—';
    return new Date(d).toLocaleDateString('en-US', {month: 'long', year: 'numeric'});
  }

  get passwordStrength(): { label: string; level: number } {
    const p = this.newPassword;
    if (!p) return {label: '', level: 0};
    if (p.length < 6) return {label: 'Weak', level: 1};
    if (p.length < 10) return {label: 'Medium', level: 2};
    const hasUpper = /[A-Z]/.test(p);
    const hasSpecial = /[^a-zA-Z0-9]/.test(p);
    if (hasUpper && hasSpecial) return {label: 'Strong', level: 4};
    if (hasUpper || hasSpecial) return {label: 'Good', level: 3};
    return {label: 'Medium', level: 2};
  }

  toggleCurrent(): void {
    this.showCurrent.set(!this.showCurrent());
  }

  toggleNew(): void {
    this.showNew.set(!this.showNew());
  }

  toggleConfirm(): void {
    this.showConfirm.set(!this.showConfirm());
  }

  submitPassword(): void {
    this.pwdError.set('');
    this.pwdSuccess.set('');

    if (!this.currentPassword || !this.newPassword || !this.confirmPassword) {
      this.pwdError.set('Please fill in all fields.');
      return;
    }
    if (this.newPassword !== this.confirmPassword) {
      this.pwdError.set('New passwords do not match.');
      return;
    }
    if (this.newPassword.length < 8) {
      this.pwdError.set('New password must be at least 8 characters.');
      return;
    }

    this.pwdLoading.set(true);
    this.userService.changePassword({
      currentPassword: this.currentPassword,
      newPassword: this.newPassword,
      confirmPassword: this.confirmPassword,
    }).subscribe({
      next: () => {
        this.pwdLoading.set(false);
        this.pwdSuccess.set('Password updated successfully.');
        this.currentPassword = '';
        this.newPassword = '';
        this.confirmPassword = '';
      },
      error: (err) => {
        this.pwdLoading.set(false);
        const msg = err?.error?.message
          ?? err?.error?.violations?.[0]?.message
          ?? 'Failed to update password.';
        this.pwdError.set(msg);
      }
    });
  }

  private toast = inject(ToastService);

  avatarUploading = signal(false);

  onAvatarSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    if (!file) return;

    if (!file.type.startsWith('image/')) {
      this.toast.show('Please select an image file.', 'error');
      return;
    }
    if (file.size > 5 * 1024 * 1024) {
      this.toast.show('Image too large (max 5MB).', 'error');
      return;
    }

    this.resizeImage(file, 256).then(dataUrl => {
      this.avatarUploading.set(true);
      this.userService.updateAvatar(dataUrl).subscribe({
        next: () => {
          this.avatarUploading.set(false);
          this.toast.show('Avatar updated.', 'success');
        },
        error: () => {
          this.avatarUploading.set(false);
          this.toast.show('Failed to update avatar.', 'error');
        }
      });
    }).catch(() => this.toast.show('Could not read the image.', 'error'));

    input.value = '';
  }

  private resizeImage(file: File, max: number): Promise<string> {
    return new Promise((resolve, reject) => {
      const reader = new FileReader();
      reader.onload = () => {
        const img = new Image();
        img.onload = () => {
          const scale = Math.min(max / img.width, max / img.height, 1);
          const w = Math.round(img.width * scale);
          const h = Math.round(img.height * scale);
          const canvas = document.createElement('canvas');
          canvas.width = w;
          canvas.height = h;
          const ctx = canvas.getContext('2d');
          if (!ctx) return reject();
          ctx.drawImage(img, 0, 0, w, h);
          resolve(canvas.toDataURL('image/jpeg', 0.85));
        };
        img.onerror = reject;
        img.src = reader.result as string;
      };
      reader.onerror = reject;
      reader.readAsDataURL(file);
    });
  }
}
