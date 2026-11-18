import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { Link } from 'react-router-dom';
import { useAuth } from '../contexts/AuthContext';
import { Field, Input } from '../components/ui/Form';
import { useToast } from '../components/ui/Toast';
import { problemDetailMessage } from '../services/api';

const schema = z.object({
  email: z.string().email('E-mail inválido'),
  password: z.string().min(1, 'Informe a senha'),
});

export function LoginPage() {
  const { login } = useAuth();
  const { notify } = useToast();
  const { register, handleSubmit, formState: { errors, isSubmitting } } =
    useForm<z.infer<typeof schema>>({ resolver: zodResolver(schema) });

  const onSubmit = handleSubmit(async (data) => {
    try {
      await login(data.email, data.password);
    } catch (error) {
      notify('error', problemDetailMessage(error));
    }
  });

  return (
    <main className="min-h-screen flex items-center justify-center p-4">
      <div className="card w-full max-w-sm">
        <div className="flex items-center gap-2 mb-6">
          <span className="w-10 h-10 rounded-xl bg-accent text-white flex items-center justify-center font-bold">IA</span>
          <div>
            <h1 className="text-lg font-bold">InvestIA</h1>
            <p className="text-xs text-muted">Suas finanças e investimentos</p>
          </div>
        </div>
        <form onSubmit={onSubmit} className="space-y-4">
          <Field label="E-mail" error={errors.email?.message}>
            <Input type="email" autoComplete="email" placeholder="voce@email.com" {...register('email')} />
          </Field>
          <Field label="Senha" error={errors.password?.message}>
            <Input type="password" autoComplete="current-password" placeholder="••••••••" {...register('password')} />
          </Field>
          <button className="btn-primary w-full" disabled={isSubmitting}>
            {isSubmitting ? 'Entrando…' : 'Entrar'}
          </button>
        </form>
        <p className="text-sm text-muted mt-4 text-center">
          Não tem conta? <Link to="/register" className="text-accent font-semibold">Criar conta</Link>
        </p>
      </div>
    </main>
  );
}
