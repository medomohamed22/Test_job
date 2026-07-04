const StellarSdk = require('@stellar/stellar-sdk');

const APP_WALLET_SECRET = process.env.APP_WALLET_SECRET;

const PI_HORIZON_URL = 'https://api.testnet.minepi.com';
const NETWORK_PASSPHRASE = 'Pi Testnet';

module.exports = async function handler(req, res) {
  if (req.method !== 'POST') {
    return res.status(405).json({
      error: 'Method Not Allowed'
    });
  }

  try {
    const { uid, username, amount, walletAddress } = req.body;

    const withdrawAmount = parseFloat(amount);

    if (!uid || !username || !amount || !walletAddress) {
      return res.status(400).json({
        error: 'بيانات ناقصة'
      });
    }

    if (!withdrawAmount || withdrawAmount <= 0 || withdrawAmount > 1) {
      return res.status(400).json({
        error: 'المبلغ غير صحيح. الحد الأقصى هو 1π'
      });
    }

    if (!walletAddress.startsWith('G') || walletAddress.length < 50) {
      return res.status(400).json({
        error: 'عنوان المحفظة غير صحيح'
      });
    }

    if (!APP_WALLET_SECRET) {
      throw new Error('APP_WALLET_SECRET is not defined in Vercel Environment Variables');
    }

    const server = new StellarSdk.Horizon.Server(PI_HORIZON_URL);

    const sourceKeys = StellarSdk.Keypair.fromSecret(APP_WALLET_SECRET);
    const sourceAccount = await server.loadAccount(sourceKeys.publicKey());

    const transaction = new StellarSdk.TransactionBuilder(sourceAccount, {
      fee: '100000',
      networkPassphrase: NETWORK_PASSPHRASE
    })
      .addOperation(
        StellarSdk.Operation.payment({
          destination: walletAddress,
          asset: StellarSdk.Asset.native(),
          amount: withdrawAmount.toFixed(7)
        })
      )
      .setTimeout(30)
      .build();

    transaction.sign(sourceKeys);

    const result = await server.submitTransaction(transaction);

    return res.status(200).json({
      success: true,
      txid: result.hash,
      message: 'تم التحويل بنجاح'
    });

  } catch (err) {
    console.error('--- ERROR LOG START ---');

    let errorResponse = {
      error: 'فشلت المعاملة',
      details: err.message
    };

    if (
      err.response &&
      err.response.data &&
      err.response.data.extras &&
      err.response.data.extras.result_codes
    ) {
      const codes = err.response.data.extras.result_codes;
      const opCodes = codes.operations ? codes.operations.join(', ') : 'no_op_code';

      errorResponse.details = `Blockchain Error: ${codes.transaction} (${opCodes})`;

      if (codes.transaction === 'tx_insufficient_fee') {
        errorResponse.error = 'رسوم الشبكة مرتفعة حالياً، حاول مرة أخرى';
      } else if (opCodes.includes('op_underfunded')) {
        errorResponse.error = 'محفظة النظام تحتاج شحن رصيد';
      } else if (opCodes.includes('op_no_destination')) {
        errorResponse.error = 'عنوان المحفظة غير موجود أو غير مفعل';
      } else if (opCodes.includes('op_malformed')) {
        errorResponse.error = 'بيانات التحويل غير صحيحة';
      }
    }

    console.error(errorResponse);
    console.error('--- ERROR LOG END ---');

    return res.status(500).json(errorResponse);
  }
};
