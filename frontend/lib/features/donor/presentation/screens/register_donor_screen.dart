import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../../app/theme/tokens/color_tokens.dart';
import '../../../../app/theme/tokens/spacing_tokens.dart';
import '../../../../app/theme/tokens/typography_tokens.dart';
import '../../../../core/errors/api_error.dart';
import '../../../../core/format/formatters.dart';
import '../../../../core/providers/core_providers.dart';
import '../../../../core/validation/donor_validators.dart';
import '../../../../data/models/blood_group.dart';
import '../../../../data/models/donor.dart';
import '../../../../shared/widgets/app_buttons.dart';
import '../../../../shared/widgets/app_card.dart';
import '../../../../shared/widgets/app_page_header.dart';
import '../../../../shared/widgets/app_select_field.dart';
import '../../../../shared/widgets/app_snack_bar.dart';
import '../../../../shared/widgets/app_text_field.dart';
import '../providers/donor_providers.dart';

/// 02 · Register donor — Donor, mobile 390 and web 1440.
class RegisterDonorScreen extends ConsumerStatefulWidget {
  const RegisterDonorScreen({super.key});

  @override
  ConsumerState<RegisterDonorScreen> createState() =>
      _RegisterDonorScreenState();
}

class _RegisterDonorScreenState extends ConsumerState<RegisterDonorScreen> {
  final _fullName = TextEditingController();
  final _cin = TextEditingController();
  final _dateOfBirth = TextEditingController();
  final _weight = TextEditingController();
  final _city = TextEditingController();
  final _phone = TextEditingController();

  DateTime? _birthDate;
  BloodGroup? _bloodGroup;
  Map<String, String> _errors = const {};
  bool _submitting = false;

  @override
  void dispose() {
    for (final controller in [
      _fullName,
      _cin,
      _dateOfBirth,
      _weight,
      _city,
      _phone,
    ]) {
      controller.dispose();
    }
    super.dispose();
  }

  void _clear(String field) {
    if (_errors.containsKey(field)) {
      setState(() => _errors = {..._errors}..remove(field));
    }
  }

  Future<void> _pickDateOfBirth() async {
    final now = ref.read(clockProvider).now();
    final picked = await showDatePicker(
      context: context,
      initialDate: _birthDate ?? DateTime(now.year - 25, now.month, now.day),
      firstDate: DateTime(now.year - 80),
      lastDate: now,
      helpText: 'Date of birth',
    );
    if (picked == null || !mounted) return;
    setState(() {
      _birthDate = picked;
      _dateOfBirth.text = AppFormatters.fullDate(picked);
      _errors = {..._errors}..remove('dateOfBirth');
    });
  }

  Future<void> _submit() async {
    final errors = <String, String>{};
    void check(String field, String? message) {
      if (message != null) errors[field] = message;
    }

    check('fullName', DonorValidators.fullName(_fullName.text));
    check('cin', DonorValidators.cin(_cin.text));
    check('dateOfBirth', DonorValidators.dateOfBirth(_birthDate));
    check('weight', DonorValidators.weight(_weight.text));
    check('bloodGroup', DonorValidators.bloodGroup(_bloodGroup));
    check('city', DonorValidators.city(_city.text));
    check('phone', DonorValidators.phone(_phone.text));

    setState(() => _errors = errors);
    if (errors.isNotEmpty) return;

    setState(() => _submitting = true);
    try {
      await ref
          .read(donorRepositoryProvider)
          .register(
            DonorDraft(
              fullName: _fullName.text.trim(),
              cin: _cin.text.replaceAll(RegExp(r'\s'), ''),
              dateOfBirth: _birthDate!,
              weightKg: int.parse(_weight.text.trim()),
              bloodGroup: _bloodGroup!,
              city: _city.text.trim(),
              phone: _phone.text.trim(),
            ),
          );
      if (!mounted) return;
      showAppSnack(
        context,
        'Account created. Next, check that you can donate.',
        tone: AppSnackTone.success,
      );
      context.go('/donor/eligibility');
    } on ApiError catch (error) {
      if (!mounted) return;
      setState(() {
        _errors = {
          ..._errors,
          ...error.fieldErrors,
          if (error.isConflict && error.fieldErrors.isEmpty)
            'cin': 'An account already exists with this ID number.',
        };
      });
      showAppSnack(
        context,
        error.isConflict
            ? 'This ID number is already registered.'
            : apiMessageFor(error),
        tone: AppSnackTone.critical,
      );
    } finally {
      if (mounted) setState(() => _submitting = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final wide = MediaQuery.sizeOf(context).width >= AppBreakpoints.wide;
    final content = Padding(
      padding: wide
          ? const EdgeInsets.fromLTRB(
              AppSpacing.page,
              28,
              AppSpacing.page,
              AppSpacing.xxxl,
            )
          : const EdgeInsets.all(AppSpacing.lg),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          AppPageHeader(
            wide: wide,
            title: 'Create a donor account',
            subtitle: wide
                ? 'Use your name exactly as written on the National ID card (CIN). Registration takes about two minutes.'
                : 'Use your name exactly as written on the National ID card (CIN).',
          ),
          const SizedBox(height: AppSpacing.xl),
          if (wide)
            Row(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Expanded(child: _form(wide: true)),
                const SizedBox(width: AppSpacing.xxl),
                const Expanded(child: _WhatHappensNext()),
              ],
            )
          else ...[
            _form(wide: false),
            const SizedBox(height: AppSpacing.lg),
            const _LegalNote(),
          ],
        ],
      ),
    );

    if (wide) return SingleChildScrollView(child: content);

    return Column(
      children: [
        Expanded(child: SingleChildScrollView(child: content)),
        _BottomAction(
          label: _submitting ? 'Creating account…' : 'Create account',
          onPressed: _submitting ? null : _submit,
        ),
      ],
    );
  }

  Widget _form({required bool wide}) {
    return AppCard(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          AppTextField(
            label: 'FULL NAME',
            controller: _fullName,
            hint: 'Amira Ben Salah',
            errorText: _errors['fullName'],
            textInputAction: TextInputAction.next,
            onChanged: (_) => _clear('fullName'),
          ),
          const SizedBox(height: AppSpacing.md),
          AppTextField(
            label: 'NATIONAL ID (CIN)',
            controller: _cin,
            hint: '09123456',
            helperText: '8 digits, as printed on your ID card.',
            errorText: _errors['cin'],
            keyboardType: TextInputType.number,
            textInputAction: TextInputAction.next,
            onChanged: (_) => _clear('cin'),
          ),
          const SizedBox(height: AppSpacing.md),
          Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Expanded(
                child: AppTextField(
                  label: 'DATE OF BIRTH',
                  controller: _dateOfBirth,
                  hint: '12 Mar 1998',
                  readOnly: true,
                  onTap: _pickDateOfBirth,
                  errorText: _errors['dateOfBirth'],
                  suffixIcon: const Icon(
                    Icons.calendar_today_outlined,
                    size: 18,
                    color: AppColors.onSurfaceVariant,
                  ),
                ),
              ),
              const SizedBox(width: AppSpacing.md),
              Expanded(
                child: AppTextField(
                  label: 'WEIGHT (KG)',
                  controller: _weight,
                  hint: '62',
                  errorText: _errors['weight'],
                  keyboardType: TextInputType.number,
                  textInputAction: TextInputAction.next,
                  onChanged: (_) => _clear('weight'),
                ),
              ),
            ],
          ),
          const SizedBox(height: AppSpacing.md),
          Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Expanded(
                child: AppSelectField<BloodGroup>(
                  label: 'BLOOD GROUP',
                  value: _bloodGroup,
                  hint: 'Select',
                  items: BloodGroup.values,
                  labelOf: (group) => group.code,
                  errorText: _errors['bloodGroup'],
                  onSelected: (group) => setState(() {
                    _bloodGroup = group;
                    _errors = {..._errors}..remove('bloodGroup');
                  }),
                ),
              ),
              const SizedBox(width: AppSpacing.md),
              Expanded(
                child: AppTextField(
                  label: 'CITY',
                  controller: _city,
                  hint: 'Sfax',
                  errorText: _errors['city'],
                  textInputAction: TextInputAction.next,
                  onChanged: (_) => _clear('city'),
                ),
              ),
            ],
          ),
          const SizedBox(height: AppSpacing.md),
          AppTextField(
            label: 'PHONE NUMBER',
            controller: _phone,
            hint: '+216 92 445 210',
            errorText: _errors['phone'],
            keyboardType: TextInputType.phone,
            textInputAction: TextInputAction.done,
            onChanged: (_) => _clear('phone'),
          ),
          if (wide) ...[
            const SizedBox(height: AppSpacing.lg),
            AppFilledButton(
              label: _submitting ? 'Creating account…' : 'Create account',
              onPressed: _submitting ? null : _submit,
            ),
            const SizedBox(height: AppSpacing.md),
            const _LegalNote(),
          ],
        ],
      ),
    );
  }
}

class _BottomAction extends StatelessWidget {
  const _BottomAction({required this.label, required this.onPressed});

  final String label;
  final VoidCallback? onPressed;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.fromLTRB(
        AppSpacing.lg,
        AppSpacing.md,
        AppSpacing.lg,
        AppSpacing.lg,
      ),
      child: AppFilledButton(
        label: label,
        height: 48,
        onPressed: onPressed,
      ),
    );
  }
}

class _LegalNote extends StatelessWidget {
  const _LegalNote();

  @override
  Widget build(BuildContext context) {
    return Text(
      'By creating an account you accept the donation charter. Personal data is '
      'handled under decree n° 2018-622 on personal data protection.',
      style: AppTypography.textTheme.bodySmall?.copyWith(
        color: AppColors.onSurfaceVariant,
      ),
    );
  }
}

/// Web-only side card: what the donor should expect after registering.
class _WhatHappensNext extends StatelessWidget {
  const _WhatHappensNext();

  @override
  Widget build(BuildContext context) {
    final text = AppTypography.textTheme;

    return AppCard(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            'WHAT HAPPENS NEXT',
            style: text.labelSmall?.copyWith(
              color: AppColors.onSurfaceVariant,
            ),
          ),
          const SizedBox(height: AppSpacing.lg),
          const _NextStep(
            index: 1,
            title: 'Book a slot',
            body: 'Pick a centre, a day and a time — you can change until you '
                'confirm.',
          ),
          const SizedBox(height: AppSpacing.lg),
          const _NextStep(
            index: 2,
            title: 'Give blood',
            body: 'The donation itself takes 8–10 minutes; plan a 45-minute '
                'visit.',
          ),
          const SizedBox(height: AppSpacing.lg),
          const _NextStep(
            index: 3,
            title: 'Get your certificate',
            body: 'A signed digital certificate is issued right after the '
                'donation.',
          ),
        ],
      ),
    );
  }
}

class _NextStep extends StatelessWidget {
  const _NextStep({required this.index, required this.title, required this.body});

  final int index;
  final String title;
  final String body;

  @override
  Widget build(BuildContext context) {
    final text = AppTypography.textTheme;

    return Row(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Container(
          width: 28,
          height: 28,
          alignment: Alignment.center,
          decoration: const BoxDecoration(
            color: AppColors.surfaceContainer,
            shape: BoxShape.circle,
          ),
          child: Text(
            '$index',
            style: text.labelMedium?.copyWith(color: AppColors.onSurface),
          ),
        ),
        const SizedBox(width: AppSpacing.md),
        Expanded(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(title, style: text.titleSmall),
              const SizedBox(height: 2),
              Text(
                body,
                style: text.bodyMedium?.copyWith(
                  color: AppColors.onSurfaceVariant,
                ),
              ),
            ],
          ),
        ),
      ],
    );
  }
}
