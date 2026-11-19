import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { Link } from 'react-router-dom';
import { useAuth } from '../contexts/AuthContext';
import { Field, Input, Switch } from '../components/ui/Form';
import { useToast } from '../components/ui/Toast';
import { problemDetailMessage } from '../services/api';

const schema = z.object({
  fullName: z.string().min(3, 'Informe seu nome'),
  email: z.string().email('E-mail inválido'),
  password: z.string().min(8, 'Mínimo de 8 caracteres'),
  acceptTerms: z.literal(true, { message: 'É necessário aceitar os termos' }),
});

export function RegisterPage() {
  const { register: registerUser } = useAuth();
  const { notify } = useToast();
  const { register, handleSubmit, formState: { errors, isSubmitting } } =
    useForm<z.infer<typeof schema>>({ resolver: zodResolver(schema) });

  const onSubmit = handleSubmit(async (data) => {
    try {
      await registerUser(data);
    } catch (error) {
      notify('error', problemDetailMessage(error));
    }
  });

  return (
    <main className="min-h-screen flex items-center justify-center p-4">
      <div className="card w-full max-w-sm">
        <h1 className="text-lg font-bold mb-1">Criar conta</h1>
        <p className="text-sm text-muted mb-6">Comece a organizar suas finanças hoje.</p>
        <form onSubmit={onSubmit} className="space-y-4">
          <Field label="Nome completo" error={errors.fullName?.message}>
            <Input autoComplete="name" placeholder="Seu nome" {...register('fullName')} />
          </Field>
          <Field label="E-mail" error={errors.email?.message}>
            <Input type="email" autoComplete="email" placeholder="voce@email.com" {...register('email')} />
          </Field>
          <Field label="Senha" error={errors.password?.message}>
            <Input type="password" autoComplete="new-password" placeholder="mínimo 8 caracteres" {...register('password')} />
          </Field>
          <Switch
            label="Aceito os termos de uso e a política de privacidade (LGPD)."
            {...register('acceptTerms')}
          />
          {errors.acceptTerms && <p className="text-xs text-danger">{errors.acceptTerms.message}</p>}
          <button className="btn-primary w-full" disabled={isSubmitting}>
            {isSubmitting ? 'Criando…' : 'Criar conta'}
          </button>
        </form>
        <p className="text-sm text-muted mt-4 text-center">
          Já tem conta? <Link to="/login" className="text-accent font-semibold">Entrar</Link>
        </p>
      </div>
    </main>
  );
}
